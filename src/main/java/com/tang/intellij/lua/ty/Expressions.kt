/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.ty

import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.psi.impl.LuaDocTagNotImpl
import com.tang.intellij.lua.comment.psi.impl.LuaDocTagTypeImpl
import com.tang.intellij.lua.ext.recursionGuard
import com.tang.intellij.lua.lang.type.LuaNumber
import com.tang.intellij.lua.lang.type.LuaString
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.psi.impl.LuaNameExprMixin
import com.tang.intellij.lua.search.GuardType
import com.tang.intellij.lua.search.SearchContext

fun inferExpr(expr: LuaExpr?, context: SearchContext): ITy {
    if (expr == null)
        return Ty.UNKNOWN

    if (expr.comment != null) {
        val typeCast = PsiTreeUtil.getChildrenOfTypeAsList(expr.comment, LuaDocTagTypeImpl::class.java).firstOrNull()

        if (typeCast != null) {
            return if (context.supportsMultipleResults) {
                typeCast.getType()
            } else typeCast.getType(context.index)
        }
    }

    var ty: ITy? = null

    if (expr is LuaIndexExpr || expr is LuaNameExpr) {
        val tree = LuaDeclarationTree.get(expr.containingFile)
        val declaration = tree.find(expr)?.firstDeclaration?.psi
        if (declaration != expr && declaration is LuaTypeGuessable) {
            ty = declaration.guessType(context)
        }
    }

    if (ty == null) {
        ty = inferExprInner(expr, context)
    }

    val notTypeCast = PsiTreeUtil.getChildrenOfTypeAsList(expr.comment, LuaDocTagNotImpl::class.java).firstOrNull()

    if (notTypeCast != null) {
        val notTy = if (context.supportsMultipleResults) {
            notTypeCast.getType()
        } else {
            notTypeCast.getType(context.index)
        }

        if (ty is TyMultipleResults) {
            val notTyList = if (notTy is TyMultipleResults) {
                notTy.list
            } else {
                listOf(notTy)
            }

            return TyMultipleResults(ty.list.mapIndexed { i, resultTy ->
                resultTy.not(notTyList.getOrNull(i) ?: Ty.VOID)
            }, ty.variadic)
        }

        return ty.not((notTy as? TyMultipleResults)?.list?.first() ?: notTy)
    }

    return ty
}

private fun inferExprInner(expr: LuaPsiElement, context: SearchContext): ITy {
    return when (expr) {
        is LuaUnaryExpr -> expr.infer(context)
        is LuaBinaryExpr -> expr.infer(context)
        is LuaCallExpr -> expr.infer(context)
        is LuaClosureExpr -> infer(expr, context)
        is LuaTableExpr -> expr.infer(context)
        is LuaParenExpr -> infer(expr.expr, context)
        is LuaNameExpr -> expr.infer(context)
        is LuaLiteralExpr -> expr.infer()
        is LuaIndexExpr -> expr.infer(context)
        else -> Ty.UNKNOWN
    }
}

private fun LuaUnaryExpr.infer(context: SearchContext): ITy {
    val stub = stub
    val operator = if (stub != null) stub.opType else unaryOp.node.firstChildNode.elementType

    return when (operator) {
        LuaTypes.MINUS -> { // Negative something
            val ty = infer(expr, context)
            return if (ty is TyPrimitiveLiteral) ty.primitiveType else ty
        }
        LuaTypes.GETN -> Ty.NUMBER // Table length is a number
        LuaTypes.NOT -> { // Returns a boolean; inverse of a boolean literal
            return when (infer(expr, context).booleanType) {
                Ty.TRUE -> Ty.FALSE
                Ty.FALSE -> Ty.TRUE
                else -> Ty.BOOLEAN
            }
        }
        else -> Ty.UNKNOWN
    }
}

private fun LuaBinaryExpr.infer(context: SearchContext): ITy {
    val stub = stub
    val operator = if (stub != null) stub.opType else {
        val firstChild = firstChild
        val nextVisibleLeaf = PsiTreeUtil.nextVisibleLeaf(firstChild)
        nextVisibleLeaf?.node?.elementType
    }
    return operator.let {
        when (it) {
        //..
            LuaTypes.CONCAT -> Ty.STRING
        //<=, ==, <, ~=, >=, >
            LuaTypes.LE, LuaTypes.EQ, LuaTypes.LT, LuaTypes.NE, LuaTypes.GE, LuaTypes.GT -> Ty.BOOLEAN
        //and, or
            LuaTypes.AND, LuaTypes.OR -> guessAndOrType(this, operator, context)
        //&, <<, |, >>, ~, ^,    +, -, *, /, //, %
            LuaTypes.BIT_AND, LuaTypes.BIT_LTLT, LuaTypes.BIT_OR, LuaTypes.BIT_RTRT, LuaTypes.BIT_TILDE, LuaTypes.EXP,
            LuaTypes.PLUS, LuaTypes.MINUS, LuaTypes.MULT, LuaTypes.DIV, LuaTypes.DOUBLE_DIV, LuaTypes.MOD -> guessBinaryOpType(this, context)
            else -> Ty.UNKNOWN
        }
    }
}

private fun guessAndOrType(binaryExpr: LuaBinaryExpr, operator: IElementType?, context:SearchContext): ITy {
    val lhs = binaryExpr.left
    val rhs = binaryExpr.right

    val lty = infer(lhs, context)

    //and
    if (operator == LuaTypes.AND) {
        return when (lty.booleanType) {
            Ty.TRUE -> infer(rhs, context)
            Ty.FALSE -> lty
            else -> {
                val tys = mutableListOf<ITy>()
                TyUnion.each(lty) {
                    if (it == Ty.BOOLEAN) {
                        tys.add(Ty.FALSE)
                    } else if (it.booleanType != Ty.TRUE) {
                        tys.add(it)
                    }
                }
                tys.add(infer(rhs, context))
                TyUnion.union(tys)
            }
        }
    }

    //or
    return when (lty.booleanType) {
        Ty.TRUE -> lty
        Ty.FALSE -> infer(rhs, context)
        else -> {
            val tys = mutableListOf<ITy>()
            TyUnion.each(lty) {
                if (it == Ty.BOOLEAN) {
                    tys.add(Ty.TRUE)
                } else if (it.booleanType != Ty.FALSE) {
                    tys.add(it)
                }
            }
            tys.add(infer(rhs, context))
            TyUnion.union(tys)
        }
    }
}

private fun guessBinaryOpType(binaryExpr : LuaBinaryExpr, context:SearchContext): ITy {
    val type = infer(binaryExpr.left, context)
    return if (type is TyPrimitiveLiteral) type.primitiveType else type
}

fun LuaCallExpr.createSubstitutor(sig: IFunSignature, context: SearchContext): ITySubstitutor {
    val selfSubstitutor = TySelfSubstitutor(context, this)

    if (sig.isGeneric()) {
        val list = mutableListOf<ITy>()
        // self type
        if (this.isMethodColonCall) {
            this.prefixExpr?.let { prefix ->
                list.add(prefix.guessType(context))
            }
        }

        for (i in 0 until argList.size - 1) {
            context.withIndex(0) { list.add(argList[i].guessType(context)) }
        }

        argList.lastOrNull()?.let {
            context.withMultipleResults {
                val lastArgTy = it.guessType(context)

                if (lastArgTy is TyMultipleResults) {
                    list.addAll(lastArgTy.list)
                } else {
                    list.add(lastArgTy)
                }
            }
        }

        val genericAnalyzer = GenericAnalyzer(sig.tyParameters, context)

        var processedIndex = -1
        sig.processParameters { index, param ->
            val arg = list.getOrNull(index)
            if (arg != null) {
                genericAnalyzer.analyze(arg, param.ty)
            }
            processedIndex = index
            true
        }
        // vararg
        val varargTy = sig.varargTy
        if (varargTy != null) {
            for (i in processedIndex + 1 until list.size) {
                val argTy = list[i]
                genericAnalyzer.analyze(argTy, varargTy)
            }
        }

        val map = genericAnalyzer.map.toMutableMap()
        sig.tyParameters?.forEach {
            val superCls = it.superClass
            if (superCls != null && Ty.isInvalid(map[it.name])) map[it.name] = superCls
        }

        val parameterSubstitutor = TyParameterSubstitutor(map)

        return TyChainSubstitutor.chain(selfSubstitutor, parameterSubstitutor)!!
    }

    return selfSubstitutor
}

private fun LuaCallExpr.getReturnTy(sig: IFunSignature, context: SearchContext): ITy {
    val substitutor = createSubstitutor(sig, context)
    val returnTy = sig.returnTy?.substitute(substitutor) ?: Ty.UNKNOWN
    return if (returnTy is TyMultipleResults) {
        if (context.supportsMultipleResults)
            returnTy
        else
            returnTy.list.getOrNull(context.index) ?: if (returnTy.variadic) returnTy.list.last() else Ty.NIL
    } else {
        if (context.supportsMultipleResults || context.index == 0)
            returnTy
        else
            Ty.NIL
    }
}

private fun LuaCallExpr.infer(context: SearchContext): ITy {
    val luaCallExpr = this
    // xxx()
    val expr = luaCallExpr.expr
    // 从 require 'xxx' 中获取返回类型
    if (expr is LuaNameExpr && LuaSettings.isRequireLikeFunctionName(expr.name)) {
        var filePath: String? = null
        val string = luaCallExpr.firstStringArg
        if (string is LuaLiteralExpr) {
            filePath = string.stringValue
        }
        var file: LuaPsiFile? = null
        if (filePath != null)
            file = resolveRequireFile(filePath, luaCallExpr.project)
        if (file != null)
            return file.guessType(context)

        return Ty.UNKNOWN
    }

    var ret: ITy = Ty.VOID
    val ty = infer(expr, context)
    Ty.eachResolved(ty, context) {
        val substitutedSignature = it.matchSignature(context, this)?.substitutedSignature

        if (substitutedSignature == null) {
            return Ty.UNKNOWN
        }

        ret = ret.union(getReturnTy(substitutedSignature, context))
    }

    // xxx.new()
    if (expr is LuaIndexExpr) {
        val fnName = expr.name
        if (fnName != null && LuaSettings.isConstructorName(fnName)) {
            ret = ret.union(expr.guessParentType(context))
        }
    }

    return if (Ty.isInvalid(ret)) Ty.UNKNOWN else TyMultipleResults.flatten(ret)
}

private fun LuaNameExpr.infer(context: SearchContext): ITy {
    val set = recursionGuard(this, Computable {
        if (name == Constants.WORD_SELF) {
            val methodDef = PsiTreeUtil.getStubOrPsiParentOfType(this, LuaClassMethodDef::class.java)
            if (methodDef != null && !methodDef.isStatic) {
                val methodName = methodDef.classMethodName
                val expr = methodName.expr
                val methodClassType = expr.guessType(context) as? ITyClass
                if (methodClassType != null) {
                    return@Computable TyClass.createSelfType(methodClassType)
                }
            }
        }

        var type:ITy = Ty.VOID

        context.withRecursionGuard(this, GuardType.GlobalName) {
            val multiResolve = multiResolve(this, context)
            var maxTimes = 10
            for (element in multiResolve) {
                val set = getType(context, element)
                type = type.union(set)
                if (--maxTimes == 0)
                    break
            }
            type
        }

        if (Ty.isInvalid(type)) {
            type = getType(context, this)
        }

        type
    })
    return set ?: Ty.UNKNOWN
}

private fun getType(context: SearchContext, def: PsiElement): ITy {
    when (def) {
        is LuaNameExpr -> {
            //todo stub.module -> ty
            val stub = def.stub
            stub?.module?.let {
                val memberType = createSerializedClass(it).guessMemberType(def.name, context)
                if (memberType != null && !Ty.isInvalid(memberType))
                    return memberType
            }

            var type: ITy = def.docTy ?: Ty.VOID
            //guess from value expr
            if (Ty.isInvalid(type) /*&& !context.forStub*/) {
                val stat = def.assignStat
                if (stat != null) {
                    val exprList = stat.valueExprList
                    if (exprList != null) {
                        val index = stat.getIndexFor(def)
                        type = context.withIndex(index, index == stat.getLastIndex()) {
                            exprList.guessTypeAt(context)
                        }
                    }
                }
            }

            //Global
            if (isGlobal(def) && def.docTy == null && type !is ITyPrimitive) {
                //use globalClassTy to store class members, that's very important
                type = type.union(TyClass.createGlobalType(def, context.forStub))
            }
            return if (Ty.isInvalid(type)) Ty.UNKNOWN else type
        }
        is LuaTypeGuessable -> return def.guessType(context)
        else -> return Ty.UNKNOWN
    }
}

private fun isGlobal(nameExpr: LuaNameExpr):Boolean {
    val minx = nameExpr as LuaNameExprMixin
    val gs = minx.greenStub
    return gs?.isGlobal ?: (resolveLocal(nameExpr, null) == null)
}

fun LuaLiteralExpr.infer(): ITy {
    return when (this.kind) {
        LuaLiteralKind.Bool -> TyPrimitiveLiteral.getTy(TyPrimitiveKind.Boolean, firstChild.text)
        LuaLiteralKind.Nil -> Ty.NIL
        LuaLiteralKind.Number -> {
            val n = LuaNumber.getValue(firstChild.text)
            if (n != null) TyPrimitiveLiteral.getTy(TyPrimitiveKind.Number, n.toString()) else Ty.UNKNOWN
        }
        LuaLiteralKind.String -> TyPrimitiveLiteral.getTy(TyPrimitiveKind.String, LuaString.getContent(firstChild.text).value)
        LuaLiteralKind.Varargs -> {
            val o = PsiTreeUtil.getParentOfType(this, LuaFuncBodyOwner::class.java)
            TyMultipleResults(listOf(o?.varargType ?: Ty.UNKNOWN), true)
        }
        else -> Ty.UNKNOWN
    }
}

private fun LuaIndexExpr.infer(context: SearchContext): ITy {
    val retTy = recursionGuard(this, Computable {
        val indexExpr = this

        //from @type annotation
        val docTy = indexExpr.docTy
        if (docTy != null)
            return@Computable docTy

        // xxx.yyy = zzz
        //from value
        var result: ITy = Ty.VOID
        val assignStat = indexExpr.assignStat
        if (assignStat != null) {
            result = context.withIndex(assignStat.getIndexFor(indexExpr), false) {
                assignStat.valueExprList?.guessTypeAt(context) ?: Ty.VOID
            }
        }

        val prefixType = indexExpr.guessParentType(context)

        Ty.eachResolved(prefixType, context) { ty ->
            result = result.union(guessFieldType(indexExpr, ty, context))
        }

        if (Ty.isInvalid(result)) Ty.UNKNOWN else result
    })

    return retTy ?: Ty.VOID
}

private fun guessFieldType(indexExpr: LuaIndexExpr, ty: ITy, context: SearchContext): ITy {
    val fieldName = indexExpr.name
    val indexTy = indexExpr.idExpr?.guessType(context)

    // _G.var = {}  <==>  var = {}
    if ((ty as? TyClass)?.className == Constants.WORD_G) {
        return if (fieldName != null) TyClass.createGlobalType(fieldName) else Ty.VOID
    }

    return fieldName?.let {
        ty.guessMemberType(it, context)
    } ?: indexTy?.let {
        ty.guessIndexerType(it, context)
    } ?: Ty.VOID
}

private fun LuaTableExpr.infer(context: SearchContext): ITy {
    val list = this.tableFieldList

    if (list.size == 0) {
        return TyTable(this)
    }

    if (list.size == 1) {
        val field = list.first()

        if (field.id == null) {
            val exprList = field.exprList

            if (exprList.size == 1) {
                val valueExpr = exprList[0]

                if (valueExpr is LuaLiteralExpr && valueExpr.kind == LuaLiteralKind.Varargs) {
                    val func = PsiTreeUtil.getStubOrPsiParentOfType(this, LuaFuncBodyOwner::class.java)
                    val ty = func?.varargType
                    if (ty != null) {
                        return TyArray(ty)
                    }
                } else {
                    return TyArray(valueExpr.guessType(context))
                }
            }
        }
    }

    return TyTable(this)
}
