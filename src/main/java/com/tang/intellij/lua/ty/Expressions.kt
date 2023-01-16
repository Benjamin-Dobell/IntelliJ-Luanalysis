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
import com.tang.intellij.lua.search.PsiSearchContext
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.search.withSearchGuard

fun inferExpr(context: SearchContext, expression: LuaExpression<*>): ITy? {
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

    val ty = context.withMultipleResults {
        if (expression is LuaIndexExpr || expression is LuaNameExpr) {
            val tree = LuaDeclarationTree.get(expression.containingFile)
            val declaration = tree.find(expression)?.firstDeclaration?.psi
            if (declaration != expression && declaration is LuaPsiTypeGuessable) {
                return@withMultipleResults declaration.guessType(context)
            }
        }

        inferExprInner(context, expression)
    }

    if (ty == null) {
        return null
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
                        resultTy.not(context, notResultTy)
                    } else {
                        resultTy
                    }
                }, flattenedTy.variadic)
            }
        }

        return TyMultipleResults.getResult(context, ty, Math.max(context.index, 0)).not(context, TyMultipleResults.getResult(context, notTy))
    }

    return if (context.supportsMultipleResults) {
        ty
    } else {
        TyMultipleResults.getResult(context, ty, context.index)
    }
}

private fun inferExprInner(context: SearchContext, expr: LuaPsiElement): ITy? {
    return when (expr) {
        is LuaUnaryExpr -> expr.infer(context)
        is LuaBinaryExpr -> expr.infer(context)
        is LuaCallExpr -> expr.infer()
        is LuaClosureExpr -> infer(context, expr)
        is LuaTableExpr -> expr.infer(context)
        is LuaParenExpr -> {
            context.withIndex(0, false) {
                infer(context, expr.expression)
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
            val ty = infer(context, expression)
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
        LuaTypes.GETN -> Primitives.NUMBER // Table length is a number
        LuaTypes.NOT -> { // Returns a boolean; inverse of a boolean literal
            return when (infer(context, expression)?.booleanType) {
                Primitives.TRUE -> Primitives.FALSE
                Primitives.FALSE -> Primitives.TRUE
                else -> Primitives.BOOLEAN
            }
        }
        else -> Primitives.UNKNOWN
    }
}

private fun LuaBinaryExpr.infer(context: SearchContext): ITy? {
    return operationType.let {
        when (it) {
        //..
            LuaTypes.CONCAT -> Primitives.STRING
        //<=, ==, <, ~=, >=, >
            LuaTypes.LE, LuaTypes.EQ, LuaTypes.LT, LuaTypes.NE, LuaTypes.GE, LuaTypes.GT -> Primitives.BOOLEAN
        //and, or
            LuaTypes.AND, LuaTypes.OR -> guessAndOrType(context, this, it)
        //&, <<, |, >>, ~, ^,    +, -, *, /, //, %
            LuaTypes.BIT_AND, LuaTypes.BIT_LTLT, LuaTypes.BIT_OR, LuaTypes.BIT_RTRT, LuaTypes.BIT_TILDE, LuaTypes.EXP,
            LuaTypes.PLUS, LuaTypes.MINUS, LuaTypes.MULT, LuaTypes.DIV, LuaTypes.DOUBLE_DIV, LuaTypes.MOD -> guessBinaryOpType(context, this)
            else -> null
        }
    }
}

private fun guessAndOrType(context: SearchContext, binaryExpr: LuaBinaryExpr, operator: IElementType?): ITy? {
    val lhs = binaryExpr.left
    val rhs = binaryExpr.right

    val lty = context.withIndex(0) { infer(context, lhs) }

    if (lty == null) {
        return null
    }

    //and
    if (operator == LuaTypes.AND) {
        return when (lty.booleanType) {
            Primitives.TRUE -> context.withIndex(0) { infer(context, rhs) }
            Primitives.FALSE -> lty
            else -> {
                val rhsTy = context.withIndex(0) { infer(context, rhs) }

                if (rhsTy == null) {
                    return null
                }

                val tys = mutableListOf<ITy>()
                Ty.eachResolved(context, lty) {
                    if (it == Primitives.BOOLEAN) {
                        tys.add(Primitives.FALSE)
                    } else if (it.booleanType != Primitives.TRUE) {
                        tys.add(it)
                    }
                }
                tys.add(rhsTy)
                TyUnion.union(context, tys)
            }
        }
    }

    //or
    return when (lty.booleanType) {
        Primitives.TRUE -> lty
        Primitives.FALSE -> context.withIndex(0) { infer(context, rhs) }
        else -> {
            val rhsTy = context.withIndex(0) { infer(context, rhs) }

            if (rhsTy == null) {
                return null
            }

            val tys = mutableListOf<ITy>()
            Ty.eachResolved(context, lty) {
                if (it == Primitives.BOOLEAN) {
                    tys.add(Primitives.TRUE)
                } else if (it.booleanType != Primitives.FALSE) {
                    tys.add(it)
                }
            }
            tys.add(rhsTy)
            TyUnion.union(context, tys)
        }
    }
}

private fun guessBinaryOpType(context: SearchContext, binaryExpr: LuaBinaryExpr): ITy? {
    val type = infer(context, binaryExpr.left)
    return if (type is TyPrimitiveLiteral) type.primitiveType else type
}

fun LuaCallExpr.createSubstitutor(sig: IFunSignature): ITySubstitutor {
    val context = PsiSearchContext(this)
    return context.withConcreteGenericSupport(false) {
        val selfSubstitutor = TySelfSubstitutor(this)
        val genericParams = sig.genericParams

        if (genericParams?.isNotEmpty() == true) {
            val list = mutableListOf<ITy>()

            // self type
            if (this.isMethodColonCall) {
                this.prefixExpression?.let { prefix ->
                    context.withIndex(0) {
                        list.add(prefix.guessType(context) ?: Primitives.UNKNOWN)
                    }
                }
            }

            for (i in 0 until argList.size - 1) {
                context.withIndex(0) {
                    list.add(argList[i].guessType(context) ?: Primitives.UNKNOWN)
                }
            }

            argList.lastOrNull()?.let {
                context.withMultipleResults {
                    val lastArgTy = it.guessType(context) ?: Primitives.UNKNOWN

                    if (lastArgTy is TyMultipleResults) {
                        list.addAll(lastArgTy.list)
                    } else {
                        list.add(lastArgTy)
                    }
                }
            }

            val paramContext = (sig as? IPsiFunSignature)?.psi?.let { PsiSearchContext(it) } ?: context
            val genericAnalyzer = GenericAnalyzer(genericParams, paramContext, this.args)

            val lastParamIndex = list.lastIndex
            var processedIndex = -1
            sig.processParameters { index, param ->
                val argTy = list.getOrNull(index)

                if (argTy != null) {
                    context.withListEntry(index == lastParamIndex) {
                        genericAnalyzer.analyze(context, argTy, param.ty ?: Primitives.UNKNOWN)
                    }
                }

                processedIndex = index
                true
            }

            // vararg
            val varargTy = sig.variadicParamTy
            if (varargTy != null) {
                for (index in processedIndex + 1 until list.size) {
                    context.withListEntry(index == lastParamIndex) {
                        genericAnalyzer.analyze(context, list[index], varargTy)
                    }
                }
            }

            val analyzedParams = genericAnalyzer.analyzedParams.toMutableMap()

            sig.genericParams?.forEach {
                val superCls = it.superClass
                if (superCls != null && Ty.isInvalid(analyzedParams[it.className])) {
                    analyzedParams[it.className] = superCls
                }
            }

            return@withConcreteGenericSupport TyChainSubstitutor.chain(selfSubstitutor, TyParameterSubstitutor(analyzedParams))
        }

        selfSubstitutor
    }
}

private fun LuaCallExpr.infer(): ITy? {
    val context = PsiSearchContext(this)
    return context.withConcreteGenericSupport(false) {
        val luaCallExpr = this
        // xxx()
        val expr = luaCallExpr.expression

        // require('module') resolution
        // TODO: Lazy module type like TyLazyClass, but with file paths for use when context.isDumb
        if (!context.isDumb && expr is LuaNameExpr && LuaSettings.isRequireLikeFunctionName(expr.name)) {
            return@withConcreteGenericSupport (luaCallExpr.firstStringArg as? LuaLiteralExpr)?.stringValue?.let {
                resolveRequireFile(it, luaCallExpr.project)
            }?.let {
                context.withMultipleResults {
                    it.guessType(context)
                }
            }
        }

        var ret: ITy = Primitives.VOID

        val ty = context.withIndex(0) {
            infer(context, expr)
        }

        if (ty == null) {
            return@withConcreteGenericSupport null
        }

        Ty.eachResolved(context, ty) {
            if (ty == Primitives.FUNCTION) {
                return@withConcreteGenericSupport TyMultipleResults(listOf(Primitives.UNKNOWN), true)
            }

            val signatureReturnTy = it.matchSignature(context, this)?.returnTy

            if (signatureReturnTy == null) {
                return@withConcreteGenericSupport null
            }

            ret = ret.union(context, signatureReturnTy)
        }

        ret
    }
}

private fun LuaNameExpr.infer(context: SearchContext): ITy? {
    return recursionGuard(this, {
        if (name == Constants.WORD_SELF) {
            val methodDef = PsiTreeUtil.getStubOrPsiParentOfType(this, LuaClassMethodDefStat::class.java)
            if (methodDef != null && !methodDef.isStatic) {
                val methodName = methodDef.classMethodName
                val expr = methodName.expression
                val methodClassType = expr.guessType(context) as? ITyClass

                if (methodClassType != null) {
                    return@recursionGuard TyClass.createSelfType(methodClassType)
                }
            }
        }

        var ty = withSearchGuard(this) {
            val multiResolve = multiResolve(context, this)
            var maxTimes = 10

            var type: ITy? = null

            for (element in multiResolve) {
                val set = getType(context, element)

                if (set == null) {
                    type = null
                    break
                }

                type = TyUnion.union(context, type, set)

                if (--maxTimes == 0)
                    break
            }

            type
        } ?: getType(context, this)

        // Global
        if (ty == null && context.isDumb && this.isGlobal()) {
            // In order to facilitate the extension of globals without needing to *explicitly* refer to the
            // underlying global variable's class by name. Since we can't look up / resolve the global's variable's
            // type during indexing, we instead attach members to a global type (identified by variable name). When
            // we process a class' members later, we also process against its aliasName i.e. name of global variable.
            // NOTE: We only need to hit this code path in "dumb mode" (i.e. during stub indexing) since that's where
            //       we create/index class members. All other times, we'll leave the ty as nil (unknown).
            ty = TyClass.createGlobalType(this)
        }

        ty
    })
}

private fun getType(context: SearchContext, def: PsiElement): ITy? {
    return when (def) {
        is LuaNameExpr -> {
            //todo stub.module -> ty
            def.stub?.module?.let {
                val memberType = createSerializedClass(it).guessMemberType(context, def.name)

                if (memberType != null) {
                    return memberType
                }
            }

            var type: ITy? = def.docTy

            if (type == null) {
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
            type
        }
        is LuaPsiTypeGuessable -> def.guessType(context)
        else -> null
    }
}

fun LuaNameExpr.isGlobal(): Boolean {
    val mixin = this as LuaNameExprMixin
    val greenStub = mixin.greenStub
    return greenStub?.isGlobal ?: (resolveLocal(null, this) == null)
}

fun LuaLiteralExpr.infer(): ITy {
    return when (this.kind) {
        LuaLiteralKind.Bool -> TyPrimitiveLiteral.getTy(TyPrimitiveKind.Boolean, firstChild.text)
        LuaLiteralKind.Nil -> Primitives.NIL
        LuaLiteralKind.Number -> {
            val n = LuaNumber.getValue(firstChild.text)
            if (n != null) TyPrimitiveLiteral.getTy(TyPrimitiveKind.Number, n.toString()) else Primitives.UNKNOWN
        }
        LuaLiteralKind.String -> TyPrimitiveLiteral.getTy(TyPrimitiveKind.String, LuaString.getContent(firstChild.text).value)
        LuaLiteralKind.Varargs -> {
            val o = PsiTreeUtil.getParentOfType(this, LuaFuncBodyOwner::class.java)
            TyMultipleResults(listOf(o?.varargType ?: Primitives.UNKNOWN), true)
        }
        else -> Primitives.UNKNOWN
    }
}

private fun LuaIndexExpr.infer(context: SearchContext): ITy? {
    return recursionGuard(this, {
        val indexExpr = this

        //from @type annotation
        val docTy = indexExpr.docTy
        if (docTy != null)
            return@recursionGuard docTy

        //from value
        var result: ITy? = null
        val prefixType = indexExpr.guessParentType(context)

        Ty.eachResolved(context, prefixType) { ty ->
            result = TyUnion.union(context, result, guessFieldType(context, indexExpr, ty))
        }

        if (result?.isUnknown != false) {
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

private fun guessFieldType(context: SearchContext, indexExpr: LuaIndexExpr, ty: ITy): ITy? {
    val fieldName = indexExpr.name
    val indexTy = indexExpr.idExpr?.guessType(context)

    // _G.var = {}  <==>  var = {}
    if ((ty as? TyClass)?.className == Constants.WORD_G) {
        return fieldName?.let { TyClass.createGlobalType(it) }
    }

    return fieldName?.let {
        ty.guessMemberType(context, it)
    } ?: indexTy?.let {
        ty.guessIndexerType(context, it)
    }
}

private fun LuaTableExpr.infer(context: SearchContext): ITy {
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
