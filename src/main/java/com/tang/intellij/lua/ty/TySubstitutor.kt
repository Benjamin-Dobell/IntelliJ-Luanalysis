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

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.RecursionManager.doPreventingRecursion
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.PsiSearchContext
import com.tang.intellij.lua.search.SearchContext
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

interface ITySubstitutor {
    val name: String;

    fun substitute(context: SearchContext, alias: ITyAlias): ITy
    fun substitute(context: SearchContext, function: ITyFunction): ITy
    fun substitute(context: SearchContext, clazz: ITyClass): ITy
    fun substitute(context: SearchContext, generic: ITyGeneric): ITy
    fun substitute(context: SearchContext, ty: ITy): ITy
}

class GenericAnalyzer(
    params: Array<out TyGenericParameter>,
    private val paramContext: SearchContext,
    val luaPsiElement: LuaPsiElement? = null
) : TyVisitor() {
    private val paramTyMap: MutableMap<String, ITy> = mutableMapOf()
    private val genericMap: Map<String, TyGenericParameter> = params.associateBy {
        it.className
    }

    private val genericParamResolutionSubstitutor = GenericParameterResolutionSubstitutor()
    private val paramSubstitutor = TyParameterSubstitutor(paramTyMap)
    private var context = paramContext

    private var cur: ITy = Primitives.VOID

    val analyzedParams: Map<String, ITy> = paramTyMap

    val visitedTys = mutableSetOf<ITy>()

    private fun isInlineTable(ty: ITy): Boolean {
        if (ty !is TyTable) {
            return false
        }

        var ancestor = ty.psi.parent

        while (ancestor is LuaTableExpr) {
            ancestor = ancestor.parent
        }

        return ancestor == luaPsiElement || (ancestor is LuaExprList && ancestor.parent == luaPsiElement)
    }

    private fun varianceFlags(ty: ITy): Int {
        return if (isInlineTable(ty)) {
            TyVarianceFlags.STRICT_UNKNOWN or TyVarianceFlags.WIDEN_TABLES
        } else TyVarianceFlags.STRICT_UNKNOWN
    }

    private fun accept(ty: ITy) {
        ProgressManager.checkCanceled()

        if (visitedTys.add(ty)) {
            ty.accept(this)
            visitedTys.remove(ty)
        }
    }

    private fun visitShape(shape: ITy) {
        Ty.eachResolved(context, cur) { source ->
            val sourceSubstitutor = source.getMemberSubstitutor(context)
            val shapeSubstitutor = shape.getMemberSubstitutor(context)

            source.processMembers(context, true) { _, sourceMember ->
                val indexTy = sourceMember.guessIndexType(context)

                val shapeMember = if (indexTy != null) {
                    shape.findIndexer(context, indexTy, false)
                } else {
                    sourceMember.name?.let { shape.findMember(context, it) }
                }

                if (shapeMember == null) {
                    return@processMembers true
                }

                val shapeMemberTy = shapeMember.guessType(context).let {
                    if (it == null) {
                        return@processMembers true
                    }

                    if (shapeSubstitutor != null) {
                        it.substitute(context, shapeSubstitutor)
                    } else {
                        it
                    }
                }

                val sourceMemberTy = (sourceMember.guessType(context) ?: Primitives.UNKNOWN).let {
                    if (sourceSubstitutor != null) it.substitute(context, sourceSubstitutor) else it
                }

                warp(sourceMemberTy) {
                    accept(Ty.resolve(context, shapeMemberTy))
                }

                true
            }
        }
    }

    fun analyze(context: SearchContext, arg: ITy, par: ITy) {
        this.context = context

        cur = arg
        warp(cur) { accept(Ty.resolve(context, par)) }
        cur = Primitives.VOID
    }

    override fun visitAlias(alias: ITyAlias) {
        accept(alias.ty)
    }

    override fun visitMultipleResults(multipleResults: TyMultipleResults) {
        val flattenedCur = TyMultipleResults.flatten(context, cur)

        if (flattenedCur is TyMultipleResults) {
            multipleResults.forResultPairs(context, flattenedCur, true) { resultMember, curMember ->
                warp(curMember ?: Primitives.NIL) {
                    accept(resultMember)
                }
            }
        } else {
            visitTy(multipleResults)
        }
    }

    override fun visitClass(clazz: ITyClass) {
        val clazzParams = clazz.params

        if (clazzParams != null) {
            Ty.eachResolved(context, cur) {
                if (it is ITyClass) {
                    it.params?.asSequence()?.zip(clazzParams.asSequence())?.forEach { (param, clazzParam) ->
                        warp(param) {
                            accept(Ty.resolve(context, clazzParam))
                        }
                    }
                }
            }
        }

        if (clazz is TyGenericParameter) {
            val genericName = clazz.className
            val genericParam = genericMap.get(genericName)

            if (genericParam != null) {
                Ty.eachResolved(context, cur) {
                    val mappedType = paramTyMap.get(genericName)
                    val currentType = it.substitute(paramContext, paramSubstitutor)
                    val substitutedGenericParam = genericParam.substitute(paramContext, genericParamResolutionSubstitutor)

                    paramTyMap[genericName] = if (substitutedGenericParam.contravariantOf(
                            context,
                            currentType,
                            TyVarianceFlags.ABSTRACT_PARAMS or TyVarianceFlags.STRICT_UNKNOWN
                        )) {
                        if (mappedType == null) {
                            currentType
                        } else if (mappedType.contravariantOf(context, currentType, varianceFlags(currentType))) {
                            mappedType
                        } else if (currentType.contravariantOf(context, mappedType, varianceFlags(mappedType))) {
                            currentType
                        } else {
                            mappedType.union(context, currentType)
                        }
                    } else {
                        genericParam
                    }
                }
            }
        } else if (clazz is IPsiTy<*> && clazz.isShape(context)) {
            visitShape(clazz)
        }
    }

    override fun visitUnion(u: TyUnion) {
        Ty.eachResolved(context, u) {
            accept(it)
        }
    }

    override fun visitArray(array: ITyArray) {
        Ty.eachResolved(context, cur) {
            if (it is ITyArray) {
                warp(it.base) {
                    accept(Ty.resolve(context, array.base))
                }
            } else if (it is ITyClass && TyArray.isArray(context, it)) {
                it.processMembers(context) { _, member ->
                    warp(member.guessType(context) ?: Primitives.UNKNOWN) {
                        accept(Ty.resolve(context, array.base))
                    }
                    true
                }
            }
        }
    }

    override fun visitFun(f: ITyFunction) {
        Ty.eachResolved(context, cur) {
            if (it is ITyFunction) {
                visitSig(it.mainSignature, f.mainSignature)
            }
        }
    }

    override fun visitGeneric(generic: ITyGeneric) {
        if (generic.base == Primitives.TABLE && generic.args.size == 2) {
            Ty.eachResolved(context, cur) { source ->
                if (source == Primitives.TABLE) {
                    warp(Primitives.UNKNOWN) {
                        accept(Ty.resolve(context, generic.args.first()))
                    }

                    warp(Primitives.UNKNOWN) {
                        accept(Ty.resolve(context, generic.args.last()))
                    }
                } else if (source is ITyArray) {
                    warp(Primitives.NUMBER) {
                        accept(Ty.resolve(context, generic.args.first()))
                    }

                    warp(source.base) {
                        accept(Ty.resolve(context, generic.args.last()))
                    }
                } else if (source.isShape(context)) {
                    val genericTable = createTableGenericFromMembers(context, source)

                    genericTable.args.asSequence().zip(generic.args.asSequence()).forEach { (param, genericParam) ->
                        warp(param) {
                            accept(Ty.resolve(context, genericParam))
                        }
                    }
                }
            }
        } else if (generic.base.isShape(context)) {
            visitShape(generic)
        }

        Ty.eachResolved(context, cur) { source ->
            if (source is ITyGeneric) {
                warp(source.base) {
                    accept(Ty.resolve(context, generic.base))
                }

                source.args.asSequence().zip(generic.args.asSequence()).forEach { (param, genericParam) ->
                    warp(param) {
                        accept(Ty.resolve(context, genericParam))
                    }
                }
            }
        }
    }

    private fun visitSig(arg: IFunSignature, par: IFunSignature) {
        arg.returnTy?.let {
            warp(it) {
                par.returnTy?.let {
                    accept(Ty.resolve(context, it))
                }
            }
        }
    }

    private fun warp(ty: ITy, action: () -> Unit) {
        if (Ty.isInvalid(ty))
            return
        val arg = cur
        cur = Ty.resolve(context, ty)
        action()
        cur = arg
    }
}

abstract class TySubstitutor : ITySubstitutor {
    override fun substitute(context: SearchContext, ty: ITy) = ty

    override fun substitute(context: SearchContext, alias: ITyAlias): ITy {
        return alias
    }

    override fun substitute(context: SearchContext, clazz: ITyClass): ITy {
        return clazz
    }

    override fun substitute(context: SearchContext, generic: ITyGeneric): ITy {
        var paramsSubstituted = false
        val substitutedArgs = generic.args.map {
            val substitutedParam = it.substitute(context, this)

            if (substitutedParam !== it) {
                paramsSubstituted = true
            }

            TyMultipleResults.getResult(context, substitutedParam)
        }

        val substitutedBase = generic.base.substitute(context, this)

        return if (paramsSubstituted || substitutedBase !== generic.base) {
            if (generic is TyDocTableGeneric) {
                TyDocTableGeneric(generic.psi, substitutedArgs.first(), substitutedArgs.last())
            } else {
                TyGeneric(substitutedArgs.toTypedArray(), substitutedBase)
            }
        } else {
            generic
        }
    }

    override fun substitute(context: SearchContext, function: ITyFunction): ITy {
        var signaturesSubstituted = false
        val substitutedSignatures = function.signatures.map {
            val substitutedSignature = it.substitute(context, this)

            if (substitutedSignature !== it) {
                signaturesSubstituted = true
            }

            substitutedSignature
        }

        val substitutedMainSignature = function.mainSignature.substitute(context, this)

        return if (signaturesSubstituted || substitutedMainSignature !== function.mainSignature) {
            TySerializedFunction(substitutedMainSignature,
                    substitutedSignatures.toTypedArray(),
                    function.flags)
        } else {
            function
        }
    }
}

// TODO: Merge into ScopedTypeSubstitutor
class TyAliasSubstitutor private constructor() : TySubstitutor() {
    override val name = "TyAliasSubstitutor"

    val processedNames = mutableSetOf<String>()

    override fun substitute(context: SearchContext, alias: ITyAlias): ITy {
        if (alias.params?.size ?: 0 == 0 && processedNames.add(alias.name)) {
            val resolved = alias.ty.substitute(context, this)
            processedNames.remove(alias.name)
            return resolved
        }

        return alias
    }

    override fun substitute(context: SearchContext, generic: ITyGeneric): ITy {
        val base = generic.base.substitute(context, this)

        if (base is ITyAlias) {
            return if (processedNames.add(base.name)) {
                val resolved = base.ty.substitute(context, generic.getMemberSubstitutor(context)).substitute(context, this)
                processedNames.remove(base.name)
                resolved
            } else Primitives.VOID
        }

        return super.substitute(context, generic)
    }

    override fun substitute(context: SearchContext, clazz: ITyClass): ITy {
        return clazz.recoverAlias(context, this)
    }

    companion object {
        fun substitute(context: SearchContext, clazz: ITy): ITy {
            return TyAliasSubstitutor().substitute(context, clazz)
        }
    }
}

class TySelfSubstitutor(val call: LuaCallExpr?, val self: ITy? = null) : TySubstitutor() {
    override val name = "TySelfSubstitutor"

    private val selfType: ITy by lazy {
        if (self != null) {
            return@lazy self
        }

        call?.let {
            val context = PsiSearchContext(call)
            it.prefixExpression?.guessType(context) ?: it.expression.guessType(context)
        } ?: Primitives.UNKNOWN
    }

    override fun substitute(context: SearchContext, clazz: ITyClass): ITy {
        if (isSelfClass(clazz)) {
            return selfType
        }
        return super.substitute(context, clazz)
    }
}

class GenericParameterResolutionSubstitutor : TySubstitutor() {
    override val name = "GenericParameterResolutionSubstitutor"

    override fun substitute(context: SearchContext, clazz: ITyClass): ITy {
        if (clazz is TyGenericParameter) {
            val superTy = clazz.getSuperType(context)
            val substitutedSuperTy = clazz.getSuperType(context)?.substitute(context, this)

            if (superTy !== substitutedSuperTy) {
                return TyGenericParameter(clazz.className, clazz.varName, substitutedSuperTy)
            }
        }

        if (clazz.willResolve(context)) {
            return doPreventingRecursion(clazz.className, false) {
                Ty.resolve(context, clazz).substitute(context, this)
            } ?: clazz
        }

        return clazz
    }
}

class TyParameterSubstitutor(val map: Map<String, ITy>) : TySubstitutor() {
    override val name = "TyParameterSubstitutor"

    override fun substitute(context: SearchContext, clazz: ITyClass): ITy {
        val ty = (clazz as? TyGenericParameter)?.let { genericParam ->
            map.get(genericParam.className) ?: genericParam
        } ?: clazz

        if (ty is TyDocTable) {
            val params = clazz.params

            // TODO: Investigate. This seems incorrect. We're just substituting the TyDocTable's generic params with
            //       this params in this substitutor. However, if the TyDocTable is declared inside a function or class
            //       there may be generic parameters in scope that were used in field types.
            if (params?.isNotEmpty() == true) {
                var paramsSubstituted = false

                val substitutedParams = params.map {
                    val substitutedParam = it.substitute(context, this)

                    if (substitutedParam != it) {
                        paramsSubstituted = true
                    }

                    substitutedParam
                }

                if (paramsSubstituted) {
                    return TyGeneric(substitutedParams.toTypedArray(), clazz)
                }
            }
        }

        return ty
    }

    companion object {
        fun withArgs(params: Array<out TyGenericParameter>, args: Array<out ITy>): TyParameterSubstitutor {
            val paramMap = mutableMapOf<String, ITy>()
            val lastIndex = minOf(params.size, args.size) - 1

            for (index in 0..lastIndex) {
                val param = params[index]
                paramMap[param.className] = args[index]
            }

            return TyParameterSubstitutor(paramMap)
        }
    }
}

class TyChainSubstitutor private constructor(val substitutors: MutableList<ITySubstitutor>) : ITySubstitutor {
    override val name = "chain:" + substitutors.joinToString(",")

    override fun substitute(context: SearchContext, alias: ITyAlias): ITy {
        return substitutors.fold(alias as ITy) { ty, subsitutor -> ty.substitute(context, subsitutor) }
    }

    override fun substitute(context: SearchContext, function: ITyFunction): ITy {
        return substitutors.fold(function as ITy) { ty, subsitutor -> ty.substitute(context, subsitutor) }
    }

    override fun substitute(context: SearchContext, clazz: ITyClass): ITy {
        return substitutors.fold(clazz as ITy) { ty, subsitutor -> ty.substitute(context, subsitutor) }
    }

    override fun substitute(context: SearchContext, generic: ITyGeneric): ITy {
        return substitutors.fold(generic as ITy) { ty, subsitutor -> ty.substitute(context, subsitutor) }
    }

    override fun substitute(context: SearchContext, ty: ITy): ITy {
        return substitutors.fold(ty) { substitutedTy, subsitutor -> substitutedTy.substitute(context, subsitutor) }
    }

    companion object {
        @ExperimentalContracts
        @JvmName("nullableChain")
        fun chain(a: ITySubstitutor?, b: ITySubstitutor?): ITySubstitutor? {
            contract {
                returns(null) implies (a == null && b == null)
                returnsNotNull() implies (a != null || b != null)
            }
            return if (a != null) {
                if (b != null) {
                    chain(a, b)
                } else {
                    a
                }
            } else b
        }

        fun chain(a: ITySubstitutor, b: ITySubstitutor): ITySubstitutor {
            return if (a is TyChainSubstitutor) {
                if (b is TyChainSubstitutor) {
                    a.substitutors.addAll(b.substitutors)
                } else {
                    a.substitutors.add(b)
                }
                a
            } else if (b is TyChainSubstitutor) {
                b.substitutors.add(0, a)
                b
            } else {
                TyChainSubstitutor(mutableListOf(a, b))
            }
        }
    }
}
