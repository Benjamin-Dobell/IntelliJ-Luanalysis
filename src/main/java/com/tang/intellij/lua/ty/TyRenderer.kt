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
import com.tang.intellij.lua.search.SearchContext

interface ITyRenderer {
    fun render(ty: ITy): String
    fun render(ty: ITy, sb: StringBuilder)
    fun renderSignature(sb: StringBuilder, signature: IFunSignature)
}

private val MaxRenderedTableMembers = 10;
private val MaxRenderedUnionMembers = 20;
private val MaxRecursionDepth = 5;
private val MaxSingleLineTableMembers = 3;
private val MaxSingleLineUnionMembers = 5;
private val MaxSingleLineGenericParams = 5;

private fun joinSingleLineOrWrap(list: Collection<String>, maxOnLine: Int, divider: String, prefix: String = "", suffix: String = "", spaceWrapItems: Boolean = prefix.isNotEmpty()): String {
    return if (list.size == 0) {
        prefix + suffix
    } else if (list.size <= maxOnLine) {
        list.joinToString(divider + " ", if (spaceWrapItems) prefix + " " else prefix, if (spaceWrapItems) " " + suffix else suffix)
    } else {
        list.joinToString(divider + "\n  ", prefix + "\n  ", "\n" + suffix)
    }
}

open class TyRenderer : TyVisitor(), ITyRenderer {
    private var visitedTys = ThreadLocal.withInitial { mutableSetOf<ITy>() }

    private fun withRecursionGuard(ty: ITy, sb: StringBuilder, block: () -> Unit) {
        if (!visitedTys.get().add(ty)) {
            sb.append("{...}");
            return;
        }

        try {
            if (visitedTys.get().size > MaxRecursionDepth) {
                sb.append("<...>")
            } else {
                block()
            }
        } finally {
            visitedTys.get().remove(ty)
        }
    }

    override fun render(ty: ITy): String {
        return buildString {
            render(ty, this)
        }
    }

    override fun render(ty: ITy, sb: StringBuilder) {
        ty.accept(object : TyVisitor() {
            override fun visitTy(visitedTy: ITy) {
                withRecursionGuard(visitedTy, sb) {
                    when (visitedTy) {
                        is ITyPrimitive -> sb.append(renderTypeName(visitedTy.displayName))
                        is TyPrimitiveLiteral -> sb.append(renderTypeName(visitedTy.displayName))
                        is TyVoid -> sb.append(renderTypeName(Constants.WORD_VOID))
                        is TyUnknown -> sb.append(renderTypeName(Constants.WORD_ANY))
                        is TyNil -> sb.append(renderTypeName(Constants.WORD_NIL))
                        is ITyGeneric -> {
                            val base = visitedTy.base

                            if (base is TyDocTable) {
                                visitClass(base)
                            } else {
                                val list = visitedTy.args.map { render(it) }
                                val baseName = if (base is ITyClass) {
                                    base.className
                                } else if (base is ITyAlias) {
                                    base.name
                                } else {
                                    base.displayName
                                }
                                sb.append("${baseName}${renderGenericParams(list)}")
                            }
                        }

                        is TyGenericParameter -> {

                        }

                        is TySnippet -> sb.append(visitedTy.toString())
                        else -> {
                            error("")
                        }
                    }
                }
            }

            override fun visitAlias(alias: ITyAlias) {
                withRecursionGuard(alias, sb) {
                    sb.append(renderAlias(alias))
                }
            }

            override fun visitClass(clazz: ITyClass) {
                withRecursionGuard(clazz, sb) {
                    sb.append(renderClass(clazz))
                }
            }

            override fun visitUnion(u: TyUnion) {
                withRecursionGuard(u, sb) {
                    val set = mutableSetOf<String>()

                    u.acceptChildren(object : TyVisitor() {
                        override fun visitTy(ty: ITy) {
                            val s = render(ty)
                            if (s.isNotEmpty()) {
                                set.add(if (isUnionPunctuationRequired(ty)) "(${s})" else s)
                            }
                        }
                    })

                    val list = set.asSequence().take(MaxRenderedUnionMembers).toMutableList()

                    if (set.size > MaxRenderedUnionMembers) {
                        list.add("...")
                    }

                    sb.append(if (set.isEmpty()) {
                        Constants.WORD_ANY
                    } else {
                        joinSingleLineOrWrap(list, MaxSingleLineUnionMembers, " |")
                    })
                }
            }

            override fun visitFun(f: ITyFunction) {
                withRecursionGuard(f, sb) {
                    sb.append("fun")
                    renderSignature(sb, f.mainSignature)
                }
            }

            override fun visitArray(array: ITyArray) {
                withRecursionGuard(array, sb) {
                    val base = array.base
                    val parenthesesRequired = isArrayPunctuationRequired(base)

                    if (parenthesesRequired) {
                        sb.append("(")
                    }

                    array.base.accept(this)

                    if (parenthesesRequired) {
                        sb.append(")")
                    }

                    sb.append("[]")
                }
            }

            override fun visitMultipleResults(multipleResults: TyMultipleResults) {
                withRecursionGuard(multipleResults, sb) {
                    val results = multipleResults.list
                    val parenthesesRequired = multipleResults.variadic && isReturnPunctuationRequired(results.last())

                    results.asSequence().take(results.size - 1).forEach {
                        sb.append(render(it))
                        sb.append(", ")
                    }

                    if (parenthesesRequired) {
                        sb.append("(")
                    }

                    sb.append(render(results.last()))

                    if (parenthesesRequired) {
                        sb.append(")")
                    }

                    if (multipleResults.variadic) {
                        sb.append("...")
                    }
                }
            }
        })
    }

    override fun renderSignature(sb: StringBuilder, signature: IFunSignature) {
        val params = signature.params
        val varargTy = signature.variadicParamTy

        if (params != null || varargTy != null) {
            sb.append("(")
            params?.forEachIndexed { i, it ->
                if (i > 0) {
                    sb.append(", ")
                }

                sb.append(it.name)

                if (it.optional) {
                    sb.append("?")
                }

                sb.append(": ")

                val paramTy = it.ty ?: Primitives.UNKNOWN
                if (isParameterPunctuationRequired(paramTy)) {
                    sb.append("(")
                    render(paramTy, sb)
                    sb.append(")")
                } else {
                    render(paramTy, sb)
                }
            }
            varargTy?.let {
                if (params?.size ?: 0 > 0) {
                    sb.append(", ")
                }

                sb.append("...: ")
                render(it, sb)
            }
            sb.append(")")
        }

        signature.returnTy?.let {
            sb.append(": ")
            if (isReturnPunctuationRequired(it)) {
                sb.append("(")
                render(it, sb)
                sb.append(")")
            } else {
                render(it, sb)
            }
        }
    }

    open fun renderGenericParams(params: Collection<String>?): String {
        return if (params != null && params.isNotEmpty()) {
            joinSingleLineOrWrap(params, MaxSingleLineGenericParams, ",", "<", ">", false)
        } else {
            ""
        }
    }

    open fun renderAlias(alias: ITyAlias): String = "${alias.name}${renderGenericParams(alias.params?.map { it.toString() })}"

    open fun renderClass(clazz: ITyClass): String {
        return when {
            clazz is TyDocTable -> {
                val context = SearchContext.get(clazz.psi.project)
                val list = mutableListOf<String>()
                clazz.psi.tableFieldList.take(MaxRenderedTableMembers).forEach { field ->
                    val name = field.name
                    val indexTy = if (name == null) field.guessIndexType(context) else null
                    val key = name ?: "[${render(indexTy ?: Primitives.VOID)}]"
                    field.valueType?.let { fieldValue ->
                        val fieldTy = if (name != null) {
                            clazz.guessMemberType(context, name)
                        } else {
                            clazz.guessIndexerType(context, indexTy!!)
                        } ?: fieldValue.getType()

                        val renderedFieldTy = render(fieldTy)

                        list.add(if (isMemberPunctuationRequired(fieldTy)) {
                            "${key}: (${renderedFieldTy})"
                        } else {
                            "${key}: ${renderedFieldTy}"
                        })
                    }
                }

                if (clazz.psi.tableFieldList.size > MaxRenderedTableMembers) {
                    list.add("...")
                }

                joinSingleLineOrWrap(list, MaxSingleLineTableMembers, ",", "{", "}")
            }
            clazz is TyGenericParameter -> {
                clazz.superClass?.let { "${clazz.varName} : ${it.displayName}" } ?: clazz.varName
            }
            clazz is TyTable && clazz.isAnonymousTable -> {
                if (clazz is TyLazySubstitutedTable) {
                    var superclass = clazz.superClass as? ITyClass

                    while (superclass != null) {
                        if (superclass.isAnonymousTable == false) {
                            return render(superclass)
                        }

                        superclass = superclass.superClass as? ITyClass
                    }
                }

                val context = SearchContext.get(clazz.psi.project)
                val list = mutableListOf<String>()
                clazz.processMembers(context) { owner, member ->
                    val name = member.name
                    val indexTy = if (name == null) member.guessIndexType(context) else null
                    val key = name ?: "[${render(indexTy ?: Primitives.VOID)}]"
                    member.guessType(context).let { fieldTy ->
                        val renderedFieldTy = render(fieldTy ?: Primitives.UNKNOWN)

                        list.add(if (isMemberPunctuationRequired(fieldTy)) {
                            "${key}: (${renderedFieldTy})"
                        } else {
                            "${key}: ${renderedFieldTy}"
                        })
                    }
                    list.size < MaxRenderedTableMembers
                }

                joinSingleLineOrWrap(list, MaxSingleLineTableMembers, ",", "{", "}")
            }
            clazz.isAnonymous -> {
                if (isSuffixedClass(clazz)) {
                    clazz.varName?.let { return it }
                }

                "[local ${clazz.varName}]"
            }
            clazz.isGlobal -> "[global ${clazz.varName}]"
            else -> "${clazz.className}${renderGenericParams(clazz.params?.map { it.toString() })}"
        }
    }

    open fun renderTypeName(t: String): String {
        return t
    }

    fun isUnionPunctuationRequired(ty: ITy): Boolean {
        return ty is TyFunction
                || ty is TyMultipleResults
                || (ty is TyGenericParameter && ty.superClass != null)
    }

    fun isMemberPunctuationRequired(ty: ITy?): Boolean {
        return ty is TyFunction
            || ty is TyMultipleResults
            || (ty is TyGenericParameter && ty.superClass != null)
    }

    fun isParameterPunctuationRequired(ty: ITy): Boolean {
        return ty is TyFunction
                || ty is TyMultipleResults
                || (ty is TyGenericParameter && ty.superClass != null)
    }

    fun isReturnPunctuationRequired(ty: ITy): Boolean {
        return ty is TyFunction
                || ty is TyUnion
                || (ty is TyGenericParameter && ty.superClass != null)
    }

    fun isArrayPunctuationRequired(ty: ITy): Boolean {
        return ty is TyFunction
                || ty is TyUnion
                || (ty is TyGenericParameter && ty.superClass != null)
    }

    companion object {
        val SIMPLE: ITyRenderer = TyRenderer()
    }
}
