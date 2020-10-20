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

import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.LuaCommentUtil
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

class GenericAnalyzer(params: Array<TyGenericParameter>, val searchContext: SearchContext, val luaPsiElement: LuaPsiElement? = null) : TyVisitor() {
    private val paramTyMap: MutableMap<String, ITy> = mutableMapOf()
    private val genericMap: Map<String, TyGenericParameter> = params.associateBy {
        it.className
    }

    private val substitutor = TyParameterSubstitutor(searchContext, paramTyMap)

    private var cur: ITy = Ty.VOID

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

    fun analyze(arg: ITy, par: ITy) {
        cur = arg
        warp(cur) { par.accept(this) }
        cur = Ty.VOID
    }

    override fun visitAlias(alias: ITyAlias) {
        alias.ty.accept(this)
    }

    override fun visitClass(clazz: ITyClass) {
        cur.let {
            val clazzParams = clazz.params

            if (clazzParams != null && it is ITyClass) {
                it.params?.asSequence()?.zip(clazzParams.asSequence())?.forEach { (param, clazzParam) ->
                    warp(param) {
                        Ty.resolve(clazzParam, searchContext).accept(this)
                    }
                }
            }
        }

        if (clazz is TyGenericParameter) {
            val genericName = clazz.className
            val genericParam = genericMap.get(genericName)

            if (genericParam != null) {
                val mappedType = paramTyMap.get(genericName)
                val currentType = cur.substitute(substitutor)

                paramTyMap[genericName] = if (genericParam.contravariantOf(currentType, searchContext, TyVarianceFlags.ABSTRACT_PARAMS or TyVarianceFlags.STRICT_UNKNOWN)) {
                    if (mappedType == null) {
                        currentType
                    } else if (mappedType.contravariantOf(currentType, searchContext, varianceFlags(currentType))) {
                        mappedType
                    } else if (currentType.contravariantOf(mappedType, searchContext, varianceFlags(mappedType))) {
                        currentType
                    } else {
                        mappedType.union(currentType, searchContext)
                    }
                } else {
                    genericParam
                }
            }
        } else if (clazz is TyDocTable) {
            clazz.processMembers(searchContext, { _, classMember ->
                val curMember = classMember.guessIndexType(searchContext)?.let {
                    cur.findIndexer(it, searchContext, false)
                } ?: classMember.name?.let { cur.findMember(it, searchContext) }

                val classMemberTy = classMember.guessType(searchContext) ?: Ty.UNKNOWN
                val curMemberTy = curMember?.guessType(searchContext) ?: Ty.NIL

                warp(curMemberTy) {
                    Ty.resolve(classMemberTy, searchContext).accept(this)
                }

                true
            }, true)
        }
    }

    override fun visitUnion(u: TyUnion) {
        Ty.eachResolved(u, searchContext) {
            it.accept(this)
        }
    }

    override fun visitArray(array: ITyArray) {
        cur.let {
            if (it is ITyArray) {
                warp(it.base) {
                    Ty.resolve(array.base, searchContext).accept(this)
                }
            } else if (it is ITyClass && TyArray.isArray(it, searchContext)) {
                it.processMembers(searchContext) { _, member ->
                    warp(member.guessType(searchContext) ?: Ty.UNKNOWN) {
                        Ty.resolve(array.base, searchContext).accept(this)
                    }
                    true
                }
            }
        }
    }

    override fun visitFun(f: ITyFunction) {
        cur.let {
            if (it is ITyFunction) {
                visitSig(it.mainSignature, f.mainSignature)
            }
        }
    }

    override fun visitGeneric(generic: ITyGeneric) {
        cur.let {
            if (it is ITyGeneric) {
                warp(it.base) {
                    Ty.resolve(generic.base, searchContext).accept(this)
                }

                it.params.asSequence().zip(generic.params.asSequence()).forEach { (param, genericParam) ->
                    warp(param) {
                        Ty.resolve(genericParam, searchContext).accept(this)
                    }
                }
            } else if (generic.base == Ty.TABLE && generic.params.size == 2) {
                if (it == Ty.TABLE) {
                    warp(Ty.UNKNOWN) {
                        Ty.resolve(generic.params.first(), searchContext).accept(this)
                    }

                    warp(Ty.UNKNOWN) {
                        Ty.resolve(generic.params.last(), searchContext).accept(this)
                    }
                } else if (it is ITyArray) {
                    warp(Ty.NUMBER) {
                        Ty.resolve(generic.params.first(), searchContext).accept(this)
                    }

                    warp(it.base) {
                        Ty.resolve(generic.params.last(), searchContext).accept(this)
                    }
                } else if (it.isShape(searchContext)) {
                    val genericTable = createTableGenericFromMembers(it, searchContext)

                    genericTable.params.asSequence().zip(generic.params.asSequence()).forEach { (param, genericParam) ->
                        warp(param) {
                            Ty.resolve(genericParam, searchContext).accept(this)
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
                    Ty.resolve(it, searchContext).accept(this)
                }
            }
        }
    }

    private fun warp(ty:ITy, action: () -> Unit) {
        if (Ty.isInvalid(ty))
            return
        val arg = cur
        cur = Ty.resolve(ty, searchContext)
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
        val substitutedParams = generic.params.map {
            val substitutedParam = it.substitute(this)

            if (substitutedParam !== it) {
                paramsSubstituted = true
            }

            TyMultipleResults.getResult(searchContext, substitutedParam)
        }

        val substitutedBase = generic.base.substitute(this)

        return if (paramsSubstituted || substitutedBase !== generic.base) {
            TyGeneric(substitutedParams.toTypedArray(), substitutedBase)
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
            } else Ty.VOID
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
        self ?: (call?.prefixExpr?.guessType(searchContext) ?: Ty.UNKNOWN)
    }

    override fun substitute(clazz: ITyClass): ITy {
        if (clazz.className.endsWith(':' + Constants.WORD_SELF)) {
            return selfType
        }
        return super.substitute(clazz)
    }
}

class TyParameterSubstitutor(searchContext: SearchContext, val map: Map<String, ITy>) : TySubstitutor(searchContext) {
    override fun substitute(clazz: ITyClass): ITy {
        if (clazz is TyGenericParameter) {
            return map.get(clazz.className) ?: clazz
        } else if (clazz is TyDocTable) {
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

        return clazz
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
