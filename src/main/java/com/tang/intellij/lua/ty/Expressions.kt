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
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.search.withSearchGuard

fun inferExpr(expression: LuaExpression<*>, context: SearchContext): ITy? {
    if (expression.comment != null) {
        val typeCast = PsiTreeUtil.getChildrenOfTypeAsList(expression.comment, LuaDocTagTypeImpl::class.java).firstOrNull()

        if (typeCast != null) {
            val castTy = typeCast.getType()

            return if (context.supportsMultipleResults) {
                castTy
            } else {
                TyMultipleResults.getResult(context, castTy, context.index)
            }
        }
    }

    var ty: ITy? = null

    if (expression is LuaIndexExpr || expression is LuaNameExpr) {
        val tree = LuaDeclarationTree.get(expression.containingFile)
        val declaration = tree.find(expression)?.firstDeclaration?.psi
        if (declaration != expression && declaration is LuaTypeGuessable) {
            ty = declaration.guessType(context)
        }
    }

    if (ty == null) {
        ty = inferExprInner(expression, context)

        if (ty == null) {
            return null
        }
    }

    val notTypeCast = PsiTreeUtil.getChildrenOfTypeAsList(expression.comment, LuaDocTagNotImpl::class.java).firstOrNull()

    if (notTypeCast != null) {
        val notTy = if (context.supportsMultipleResults) {
            notTypeCast.getType()
        } else {
            notTypeCast.getType(context.index)
        }

        if (notTy is TyMultipleResults) {
            val flattenedTy = TyMultipleResults.flatten(context, ty)

            if (flattenedTy is TyMultipleResults) {
                return TyMultipleResults(flattenedTy.convolve(context, notTy) { resultTy, notResultTy ->
                    if (notResultTy != null) {
                        resultTy.not(notResultTy, context)
                    } else {
                        resultTy
                    }
                }, flattenedTy.variadic)
            }
        }

        return TyMultipleResults.getResult(context, ty).not(TyMultipleResults.getResult(context, notTy), context)
    }

    return ty
}

private fun inferExprInner(expr: LuaPsiElement, context: SearchContext): ITy? {
    return when (expr) {
        is LuaUnaryExpr -> expr.infer(context)
        is LuaBinaryExpr -> expr.infer(context)
        is LuaCallExpr -> expr.infer(context)
        is LuaClosureExpr -> infer(expr, context)
        is LuaTableExpr -> expr.infer(context)
        is LuaParenExpr -> {
            context.withIndex(0, false) {
                infer(expr.expression, context)
            }
        }
        is LuaNameExpr -> expr.infer(context)
        is LuaLiteralExpr -> expr.infer()
        is LuaIndexExpr -> expr.infer(context)
        else -> null
    }
}

private fun LuaUnaryExpr.infer(context: SearchContext): ITy? {
    val stub = stub
    val operator = if (stub != null) stub.opType else unaryOp.node.firstChildNode.elementType

    return when (operator) {
        LuaTypes.MINUS -> { // Negative something
            val ty = infer(expression, context)
            return if (ty is TyPrimitiveLiteral) {
                when (ty.primitiveKind) {
                    TyPrimitiveKind.Number -> {
                        val value = if (ty.value.startsWith("-")) ty.value.substring(1) else "-${ty.value}"
                        TyPrimitiveLiteral.getTy(TyPrimitiveKind.Number, value)
                    }
                    TyPrimitiveKind.String -> { // Unfortunately, Lua has implicit string to number coercion
                        val negative = ty.value.startsWith("-")
                        val number = LuaNumber.getValue(if (negative) ty.value.substring(1) else ty.value)

                        if (number != null) {
                            val value = if (negative) number.toString() else "-${number}"
                            TyPrimitiveLiteral.getTy(TyPrimitiveKind.Number, value)
                        } else ty.primitiveType
                    }
                    else -> ty.primitiveType
                }
            } else ty
        }
        LuaTypes.GETN -> Ty.NUMBER // Table length is a number
        LuaTypes.NOT -> { // Returns a boolean; inverse of a boolean literal
            return when (infer(expression, context)?.booleanType) {
                Ty.TRUE -> Ty.FALSE
                Ty.FALSE -> Ty.TRUE
                else -> Ty.BOOLEAN
            }
        }
        else -> Ty.UNKNOWN
    }
}

private fun LuaBinaryExpr.infer(context: SearchContext): ITy? {
    return operationType.let {
        when (it) {
        //..
            LuaTypes.CONCAT -> Ty.STRING
        //<=, ==, <, ~=, >=, >
            LuaTypes.LE, LuaTypes.EQ, LuaTypes.LT, LuaTypes.NE, LuaTypes.GE, LuaTypes.GT -> Ty.BOOLEAN
        //and, or
            LuaTypes.AND, LuaTypes.OR -> guessAndOrType(this, it, context)
        //&, <<, |, >>, ~, ^,    +, -, *, /, //, %
            LuaTypes.BIT_AND, LuaTypes.BIT_LTLT, LuaTypes.BIT_OR, LuaTypes.BIT_RTRT, LuaTypes.BIT_TILDE, LuaTypes.EXP,
            LuaTypes.PLUS, LuaTypes.MINUS, LuaTypes.MULT, LuaTypes.DIV, LuaTypes.DOUBLE_DIV, LuaTypes.MOD -> guessBinaryOpType(this, context)
            else -> null
        }
    }
}

private fun guessAndOrType(binaryExpr: LuaBinaryExpr, operator: IElementType?, context: SearchContext): ITy? {
    val lhs = binaryExpr.left
    val rhs = binaryExpr.right

    val lty = context.withIndex(0) { infer(lhs, context) }

    if (lty == null) {
        return null
    }

    //and
    if (operator == LuaTypes.AND) {
        return when (lty.booleanType) {
            Ty.TRUE -> context.withIndex(0) { infer(rhs, context) }
            Ty.FALSE -> lty
            else -> {
                val rhsTy = context.withIndex(0) { infer(rhs, context) }

                if (rhsTy == null) {
                    return null
                }

                val tys = mutableListOf<ITy>()
                Ty.eachResolved(lty, context) {
                    if (it == Ty.BOOLEAN) {
                        tys.add(Ty.FALSE)
                    } else if (it.booleanType != Ty.TRUE) {
                        tys.add(it)
                    }
                }
                tys.add(rhsTy)
                TyUnion.union(tys, context)
            }
        }
    }

    //or
    return when (lty.booleanType) {
        Ty.TRUE -> lty
        Ty.FALSE -> context.withIndex(0) { infer(rhs, context) }
        else -> {
            val rhsTy = context.withIndex(0) { infer(rhs, context) }

            if (rhsTy == null) {
                return null
            }

            val tys = mutableListOf<ITy>()
            Ty.eachResolved(lty, context) {
                if (it == Ty.BOOLEAN) {
                    tys.add(Ty.TRUE)
                } else if (it.booleanType != Ty.FALSE) {
                    tys.add(it)
                }
            }
            tys.add(rhsTy)
            TyUnion.union(tys, context)
        }
    }
}

private fun guessBinaryOpType(binaryExpr: LuaBinaryExpr, context: SearchContext): ITy? {
    val type = infer(binaryExpr.left, context)
    return if (type is TyPrimitiveLiteral) type.primitiveType else type
}

fun LuaCallExpr.createSubstitutor(sig: IFunSignature, context: SearchContext): ITySubstitutor {
    val selfSubstitutor = TySelfSubstitutor(context, this)
    val genericParams = sig.genericParams

    if (genericParams?.isNotEmpty() == true) {
        val list = mutableListOf<ITy>()
        // self type
        if (this.isMethodColonCall) {
            this.prefixExpression?.let { prefix ->
                list.add(prefix.guessType(context) ?: Ty.UNKNOWN)
            }
        }

        for (i in 0 until argList.size - 1) {
            context.withIndex(0) {
                list.add(argList[i].guessType(context) ?: Ty.UNKNOWN)
            }
        }

        argList.lastOrNull()?.let {
            context.withMultipleResults {
                val lastArgTy = it.guessType(context) ?: Ty.UNKNOWN

                if (lastArgTy is TyMultipleResults) {
                    list.addAll(lastArgTy.list)
                } else {
                    list.add(lastArgTy)
                }
            }
        }

        val genericAnalyzer = GenericAnalyzer(genericParams, context, this.args)

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
        val varargTy = sig.variadicParamTy
        if (varargTy != null) {
            for (i in processedIndex + 1 until list.size) {
                val argTy = list[i]
                genericAnalyzer.analyze(argTy, varargTy)
            }
        }

        val analyzedParams = genericAnalyzer.analyzedParams.toMutableMap()

        sig.genericParams?.forEach {
            val superCls = it.superClass
            if (superCls != null && Ty.isInvalid(analyzedParams[it.name])) analyzedParams[it.name] = superCls
        }

        return TyChainSubstitutor.chain(selfSubstitutor, TyParameterSubstitutor(context, analyzedParams))!!
    }

    return selfSubstitutor
}

private fun LuaCallExpr.getReturnTy(sig: IFunSignature, context: SearchContext): ITy? {
    val substitutor = createSubstitutor(sig, context)
    val returnTy = sig.returnTy?.substitute(substitutor) ?: TyMultipleResults(listOf(Ty.UNKNOWN), true)

    return if (context.supportsMultipleResults) {
        returnTy
    } else {
        TyMultipleResults.getResult(context, returnTy, context.index)
    }
}

private fun LuaCallExpr.infer(context: SearchContext): ITy? {
    val luaCallExpr = this
    // xxx()
    val expr = luaCallExpr.expression

    // require('module') resolution
    // TODO: Lazy module type like TyLazyClass, but with file paths for use when context.isDumb
    if (!context.isDumb && expr is LuaNameExpr && LuaSettings.isRequireLikeFunctionName(expr.name)) {
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

        return null
    }

    var ret: ITy = Ty.VOID
    val ty = infer(expr, context)

    if (ty == null) {
        return null
    }

    Ty.eachResolved(ty, context) {
        if (ty == Ty.FUNCTION) {
            return TyMultipleResults(listOf(Ty.UNKNOWN), true)
        }

        val returnTy = it.matchSignature(context, this)?.substitutedSignature?.let {
            getReturnTy(it, context)
        }

        if (returnTy == null) {
            return null
        }

        ret = ret.union(returnTy, context)
    }

    return ret
}

private fun LuaNameExpr.infer(context: SearchContext): ITy? {
    return recursionGuard(this, Computable {
        if (name == Constants.WORD_SELF) {
            val methodDef = PsiTreeUtil.getStubOrPsiParentOfType(this, LuaClassMethodDefStat::class.java)
            if (methodDef != null && !methodDef.isStatic) {
                val methodName = methodDef.classMethodName
                val expr = methodName.expression
                val methodClassType = expr.guessType(context) as? ITyClass

                if (methodClassType != null) {
                    return@Computable TyClass.createSelfType(methodClassType)
                }
            }
        }

        withSearchGuard(this) {
            val multiResolve = multiResolve(this, context)
            var maxTimes = 10

            var type: ITy? = null

            for (element in multiResolve) {
                val set = getType(context, element)

                if (set == null) {
                    type = null
                    break
                }

                type = TyUnion.union(type, set, context)

                if (--maxTimes == 0)
                    break
            }

            type
        } ?: getType(context, this)
    })
}

private fun getType(context: SearchContext, def: PsiElement): ITy? {
    return when (def) {
        is LuaNameExpr -> {
            //todo stub.module -> ty
            def.stub?.module?.let {
                val memberType = createSerializedClass(it).guessMemberType(def.name, context)

                if (memberType != null) {
                    return memberType
                }
            }

            var type: ITy? = def.docTy

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
            if (type != null && isGlobal(def) && def.docTy == null && type !is ITyPrimitive) {
                // Explicitly instantiating a union (not calling the type.union()) as the global type resolves to type,
                // and hence we would have just got type back. We're creating a union because we need to ensure members
                // are indexed against the global name (for completion) as well as the other type (for type resolution).
                type = TyUnion(listOf(type, TyClass.createGlobalType(def)))
            }

            type
        }
        is LuaTypeGuessable -> def.guessType(context)
        else -> null
    }
}

private fun isGlobal(nameExpr: LuaNameExpr): Boolean {
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

private fun LuaIndexExpr.infer(context: SearchContext): ITy? {
    return recursionGuard(this, Computable {
        val indexExpr = this

        //from @type annotation
        val docTy = indexExpr.docTy
        if (docTy != null)
            return@Computable docTy

        //from value
        var result: ITy? = null
        val prefixType = indexExpr.guessParentType(context)

        Ty.eachResolved(prefixType, context) { ty ->
            result = TyUnion.union(result, guessFieldType(indexExpr, ty, context), context)
        }

        if (result == null) {
            // xxx.yyy = zzz
            result = indexExpr.assignStat?.let {
                context.withIndex(it.getIndexFor(indexExpr), false) {
                    it.valueExprList?.guessTypeAt(context)
                }
            }
        }

        result
    })
}

private fun guessFieldType(indexExpr: LuaIndexExpr, ty: ITy, context: SearchContext): ITy? {
    val fieldName = indexExpr.name
    val indexTy = indexExpr.idExpr?.guessType(context)

    // _G.var = {}  <==>  var = {}
    if ((ty as? TyClass)?.className == Constants.WORD_G) {
        return fieldName?.let { TyClass.createGlobalType(it) }
    }

    return fieldName?.let {
        ty.guessMemberType(it, context)
    } ?: indexTy?.let {
        ty.guessIndexerType(it, context)
    }
}

private fun LuaTableExpr.infer(context: SearchContext): ITy? {
    val list = this.tableFieldList

    if (list.size == 0) {
        return TyTable(this)
    }

    if (list.size == 1) {
        val field = list.first()

        if (field.id == null) {
            val exprList = field.expressionList

            if (exprList.size == 1) {
                val valueExpr = exprList[0]

                if (valueExpr is LuaLiteralExpr && valueExpr.kind == LuaLiteralKind.Varargs) {
                    val func = PsiTreeUtil.getStubOrPsiParentOfType(this, LuaFuncBodyOwner::class.java)
                    val ty = func?.varargType
                    if (ty != null) {
                        return TyArray(ty)
                    }
                }
            }
        }
    }

    return TyTable(this)
}
