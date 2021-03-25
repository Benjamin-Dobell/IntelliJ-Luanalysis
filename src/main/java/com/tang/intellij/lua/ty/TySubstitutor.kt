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

import com.intellij.openapi.util.RecursionManager.doPreventingRecursion
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext

interface ITySubstitutor {
    val searchContext: SearchContext

    fun substitute(alias: ITyAlias): ITy
    fun substitute(function: ITyFunction): ITy
    fun substitute(clazz: ITyClass): ITy
    fun substitute(generic: ITyGeneric): ITy
    fun substitute(ty: ITy): ITy
}

class GenericAnalyzer(params: Array<out TyGenericParameter>, paramContext: SearchContext, val luaPsiElement: LuaPsiElement? = null) : TyVisitor() {
    private val paramTyMap: MutableMap<String, ITy> = mutableMapOf()
    private val genericMap: Map<String, TyGenericParameter> = params.associateBy {
        it.className
    }

    private val genericParamResolutionSubstitutor = GenericParameterResolutionSubstitutor(paramContext)
    private val paramSubstitutor = TyParameterSubstitutor(paramContext, paramTyMap)
    private var context = paramContext

    private var cur: ITy = Primitives.VOID

    val analyzedParams: Map<String, ITy> = paramTyMap

    private fun isInlineTable(ty: ITy): Boolean {
        if (ty !is TyTable) {
            return false
        }

        var ancestor = ty.table.parent

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

    fun analyze(arg: ITy, par: ITy, context: SearchContext) {
        this.context = context

        cur = arg
        warp(cur) { par.accept(this) }
        cur = Primitives.VOID
    }

    override fun visitAlias(alias: ITyAlias) {
        alias.ty.accept(this)
    }

    override fun visitMultipleResults(multipleResults: TyMultipleResults) {
        val flattenedCur = TyMultipleResults.flatten(context, cur)

        if (flattenedCur is TyMultipleResults) {
            multipleResults.forResultPairs(context, flattenedCur, true) { resultMember, curMember ->
                warp(curMember ?: Primitives.NIL) {
                    resultMember.accept(this)
                }
            }
        } else {
            visitTy(multipleResults)
        }
    }

    override fun visitClass(clazz: ITyClass) {
        Ty.eachResolved(cur, context) {
            val clazzParams = clazz.params

            if (clazzParams != null && it is ITyClass) {
                it.params?.asSequence()?.zip(clazzParams.asSequence())?.forEach { (param, clazzParam) ->
                    warp(param) {
                        Ty.resolve(clazzParam, context).accept(this)
                    }
                }
            }

            if (clazz is TyGenericParameter) {
                val genericName = clazz.className
                val genericParam = genericMap.get(genericName)

                if (genericParam != null) {
                    val mappedType = paramTyMap.get(genericName)
                    val currentType = it.substitute(paramSubstitutor)
                    val substitutedGenericParam = genericParam.substitute(genericParamResolutionSubstitutor)

                    paramTyMap[genericName] = if (substitutedGenericParam.contravariantOf(currentType, context, TyVarianceFlags.ABSTRACT_PARAMS or TyVarianceFlags.STRICT_UNKNOWN)) {
                        if (mappedType == null) {
                            currentType
                        } else if (mappedType.contravariantOf(currentType, context, varianceFlags(currentType))) {
                            mappedType
                        } else if (currentType.contravariantOf(mappedType, context, varianceFlags(mappedType))) {
                            currentType
                        } else {
                            mappedType.union(currentType, context)
                        }
                    } else {
                        genericParam
                    }
                }
            } else if (clazz is TyDocTable) {
                clazz.processMembers(context, true) { _, classMember ->
                    val curMember = classMember.guessIndexType(context)?.let { indexTy ->
                        it.findEffectiveIndexer(indexTy, context, false)
                    } ?: classMember.name?.let { name -> it.findEffectiveMember(name, context) }

                    val classMemberTy = classMember.guessType(context) ?: Primitives.UNKNOWN
                    val curMemberTy = curMember?.guessType(context) ?: Primitives.NIL

                    warp(curMemberTy) {
                        Ty.resolve(classMemberTy, context).accept(this)
                    }

                    true
                }
            }
        }
    }

    override fun visitUnion(u: TyUnion) {
        Ty.eachResolved(u, context) {
            it.accept(this)
        }
    }

    override fun visitArray(array: ITyArray) {
        Ty.eachResolved(cur, context) {
            if (it is ITyArray) {
                warp(it.base) {
                    Ty.resolve(array.base, context).accept(this)
                }
            } else if (it is ITyClass && TyArray.isArray(it, context)) {
                it.processMembers(context) { _, member ->
                    warp(member.guessType(context) ?: Primitives.UNKNOWN) {
                        Ty.resolve(array.base, context).accept(this)
                    }
                    true
                }
            }
        }
    }

    override fun visitFun(f: ITyFunction) {
        Ty.eachResolved(cur, context) {
            if (it is ITyFunction) {
                visitSig(it.mainSignature, f.mainSignature)
            }
        }
    }

    override fun visitGeneric(generic: ITyGeneric) {
        Ty.eachResolved(cur, context) {
            if (it is ITyGeneric) {
                warp(it.base) {
                    Ty.resolve(generic.base, context).accept(this)
                }

                it.args.asSequence().zip(generic.args.asSequence()).forEach { (param, genericParam) ->
                    warp(param) {
                        Ty.resolve(genericParam, context).accept(this)
                    }
                }
            } else if (generic.base == Primitives.TABLE && generic.args.size == 2) {
                if (it == Primitives.TABLE) {
                    warp(Primitives.UNKNOWN) {
                        Ty.resolve(generic.args.first(), context).accept(this)
                    }

                    warp(Primitives.UNKNOWN) {
                        Ty.resolve(generic.args.last(), context).accept(this)
                    }
                } else if (it is ITyArray) {
                    warp(Primitives.NUMBER) {
                        Ty.resolve(generic.args.first(), context).accept(this)
                    }

                    warp(it.base) {
                        Ty.resolve(generic.args.last(), context).accept(this)
                    }
                } else if (it.isShape(context)) {
                    val genericTable = createTableGenericFromMembers(it, context)

                    genericTable.args.asSequence().zip(generic.args.asSequence()).forEach { (param, genericParam) ->
                        warp(param) {
                            Ty.resolve(genericParam, context).accept(this)
                        }
                    }
                }
            }
        }
    }

    private fun visitSig(arg: IFunSignature, par: IFunSignature) {
        arg.returnTy?.let {
            warp(it) {
                par.returnTy?.let {
                    Ty.resolve(it, context).accept(this)
                }
            }
        }
    }

    private fun warp(ty:ITy, action: () -> Unit) {
        if (Ty.isInvalid(ty))
            return
        val arg = cur
        cur = Ty.resolve(ty, context)
        action()
        cur = arg
    }
}

open class TySubstitutor(override val searchContext: SearchContext) : ITySubstitutor {
    override fun substitute(ty: ITy) = ty

    override fun substitute(alias: ITyAlias): ITy {
        return alias
    }

    override fun substitute(clazz: ITyClass): ITy {
        return clazz
    }

    override fun substitute(generic: ITyGeneric): ITy {
        var paramsSubstituted = false
        val substitutedArgs = generic.args.map {
            val substitutedParam = it.substitute(this)

            if (substitutedParam !== it) {
                paramsSubstituted = true
            }

            TyMultipleResults.getResult(searchContext, substitutedParam)
        }

        val substitutedBase = generic.base.substitute(this)

        return if (paramsSubstituted || substitutedBase !== generic.base) {
            if (generic is TyDocTableGeneric) {
                TyDocTableGeneric(generic.genericTableTy, substitutedArgs.first(), substitutedArgs.last())
            } else {
                TyGeneric(substitutedArgs.toTypedArray(), substitutedBase)
            }
        } else {
            generic
        }
    }

    override fun substitute(function: ITyFunction): ITy {
        var signaturesSubstituted = false
        val substitutedSignatures = function.signatures.map {
            val substitutedSignature = it.substitute(this)

            if (substitutedSignature !== it) {
                signaturesSubstituted = true
            }

            substitutedSignature
        }

        val substitutedMainSignature = function.mainSignature.substitute(this)

        return if (signaturesSubstituted || substitutedMainSignature !== function.mainSignature) {
            TySerializedFunction(substitutedMainSignature,
                    substitutedSignatures.toTypedArray(),
                    function.flags)
        } else {
            function
        }
    }
}

class TyAliasSubstitutor private constructor(searchContext: SearchContext) : TySubstitutor(searchContext) {
    val processedNames = mutableSetOf<String>()

    override fun substitute(alias: ITyAlias): ITy {
        if (alias.params?.size ?: 0 == 0 && processedNames.add(alias.name)) {
            val resolved = alias.ty.substitute(this)
            processedNames.remove(alias.name)
            return resolved
        }

        return alias
    }

    override fun substitute(generic: ITyGeneric): ITy {
        val base = generic.base.substitute(this)

        if (base is ITyAlias) {
            return if (processedNames.add(base.name)) {
                val resolved = base.ty.substitute(generic.getMemberSubstitutor(searchContext)).substitute(this)
                processedNames.remove(base.name)
                resolved
            } else Primitives.VOID
        }

        return super.substitute(generic)
    }

    override fun substitute(clazz: ITyClass): ITy {
        return clazz.recoverAlias(searchContext, this)
    }

    companion object {
        fun substitute(ty: ITy, context: SearchContext): ITy {
            return ty.substitute(TyAliasSubstitutor(context))
        }
    }
}

class TySelfSubstitutor(context: SearchContext, val call: LuaCallExpr?, val self: ITy? = null) : TySubstitutor(context) {
    private val selfType: ITy by lazy {
        if (self != null) {
            return@lazy self
        }

        call?.let {
            it.prefixExpression?.guessType(searchContext) ?: it.expression.guessType(searchContext)
        } ?: Primitives.UNKNOWN
    }

    override fun substitute(clazz: ITyClass): ITy {
        if (clazz.className.endsWith(Constants.SUFFIX_CLASS_SELF)) {
            return selfType
        }
        return super.substitute(clazz)
    }
}

class GenericParameterResolutionSubstitutor(searchContext: SearchContext) : TySubstitutor(searchContext) {
    override fun substitute(clazz: ITyClass): ITy {
        if (clazz is TyGenericParameter) {
            val superTy = clazz.getSuperClass(searchContext)
            val substitutedSuperTy = clazz.getSuperClass(searchContext)?.substitute(this)

            if (superTy !== substitutedSuperTy) {
                return TyGenericParameter(clazz.name, clazz.varName, substitutedSuperTy)
            }
        }

        if (clazz.willResolve(searchContext)) {
            return doPreventingRecursion(clazz.className, false) {
                Ty.resolve(clazz, searchContext).substitute(this)
            } ?: clazz
        }

        return clazz
    }
}

class TyParameterSubstitutor(searchContext: SearchContext, val map: Map<String, ITy>) : TySubstitutor(searchContext) {
    override fun substitute(clazz: ITyClass): ITy {
        val ty = (clazz as? TyGenericParameter)?.let { genericParam ->
            map.get(genericParam.className) ?: genericParam
        } ?: clazz

        if (ty is TyDocTable) {
            val params = clazz.params

            if (params?.isNotEmpty() == true) {
                var paramsSubstituted = false

                val substitutedParams = params.map {
                    val substitutedParam = it.substitute(this)

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
        fun withArgs(context: SearchContext, params: Array<out TyGenericParameter>, args: Array<out ITy>): TyParameterSubstitutor {
            val paramMap = mutableMapOf<String, ITy>()
            val lastIndex = minOf(params.size, args.size) - 1

            for (index in 0..lastIndex) {
                val param = params[index]
                paramMap[param.className] = args[index]
            }

            return TyParameterSubstitutor(context, paramMap)
        }
    }
}

class TyChainSubstitutor private constructor(a: ITySubstitutor, b: ITySubstitutor) : ITySubstitutor {
    val substitutors = mutableListOf<ITySubstitutor>()

    override val searchContext: SearchContext

    init {
        searchContext = a.searchContext

        substitutors.add(a)
        substitutors.add(b)
    }

    companion object {
        fun chain(a: ITySubstitutor?, b: ITySubstitutor?): ITySubstitutor? {
            return if (a != null) {
                if (a is TyChainSubstitutor) {
                    b?.let {
                        if (it is TyChainSubstitutor) {
                            a.substitutors.addAll(it.substitutors)
                        } else {
                            a.substitutors.add(it)
                        }
                    }
                    a
                } else {
                    if (b != null) {
                        if (b is TyChainSubstitutor) {
                            b.substitutors.add(0, a)
                            b
                        } else TyChainSubstitutor(a, b)
                    } else a
                }
            } else b
        }
    }

    override fun substitute(alias: ITyAlias): ITy {
        return substitutors.fold(alias as ITy) { ty, subsitutor -> ty.substitute(subsitutor) }
    }

    override fun substitute(function: ITyFunction): ITy {
        return substitutors.fold(function as ITy) { ty, subsitutor -> ty.substitute(subsitutor) }
    }

    override fun substitute(clazz: ITyClass): ITy {
        return substitutors.fold(clazz as ITy) { ty, subsitutor -> ty.substitute(subsitutor) }
    }

    override fun substitute(generic: ITyGeneric): ITy {
        return substitutors.fold(generic as ITy) { ty, subsitutor -> ty.substitute(subsitutor) }
    }

    override fun substitute(ty: ITy): ITy {
        return substitutors.fold(ty) { substitutedTy, subsitutor -> substitutedTy.substitute(subsitutor) }
    }
}
