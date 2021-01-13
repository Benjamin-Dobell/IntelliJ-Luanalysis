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

open class TyRenderer : TyVisitor(), ITyRenderer {

    override fun render(ty: ITy): String {
        return buildString { render(ty, this) }
    }

    override fun render(ty: ITy, sb: StringBuilder) {
        ty.accept(object : TyVisitor() {
            override fun visitTy(ty: ITy) {
                when (ty) {
                    is ITyPrimitive -> sb.append(renderType(ty.displayName))
                    is TyPrimitiveLiteral -> sb.append(renderType(ty.displayName))
                    is TyVoid -> sb.append(renderType(Constants.WORD_VOID))
                    is TyUnknown -> sb.append(renderType(Constants.WORD_ANY))
                    is TyNil -> sb.append(renderType(Constants.WORD_NIL))
                    is ITyGeneric -> {
                        val base = ty.base

                        if (base is TyDocTable) {
                            visitClass(base)
                        } else {
                            val list = mutableListOf<String>()
                            ty.args.forEach { list.add(it.displayName) }

                            val baseName = if (base is ITyClass) base.className else base.displayName
                            sb.append("${baseName}${renderParamsList(list)}")
                        }
                    }
                    is TyGenericParameter -> {

                    }
                    is TySnippet -> sb.append(ty.toString())
                    else -> {
                        error("")
                    }
                }
            }

            override fun visitAlias(alias: ITyAlias) {
                sb.append(alias.name)
            }

            override fun visitClass(clazz: ITyClass) {
                sb.append(renderClass(clazz))
            }

            override fun visitUnion(u: TyUnion) {
                val list = mutableSetOf<String>()
                u.acceptChildren(object : TyVisitor() {
                    override fun visitTy(ty: ITy) {
                        val s = render(ty)
                        if (s.isNotEmpty()) {
                            list.add(if (ty is ITyFunction || ty is TyMultipleResults) "(${s})" else s)
                        }
                    }
                })
                sb.append(if (list.isEmpty()) Constants.WORD_ANY else list.joinToString(" | "))
            }

            override fun visitFun(f: ITyFunction) {
                sb.append("fun")
                renderSignature(sb, f.mainSignature)
            }

            override fun visitArray(array: ITyArray) {
                val base = array.base
                val parenthesesRequired = base is TyUnion || (base is TyGenericParameter && base.superClass != null)

                if (parenthesesRequired) {
                    sb.append("(")
                }

                array.base.accept(this)

                if (parenthesesRequired) {
                    sb.append(")")
                }

                sb.append("[]")
            }

            override fun visitMultipleResults(multipleResults: TyMultipleResults) {
                val list = multipleResults.list.map { render(it) }
                sb.append(list.joinToString(", "))
                if (multipleResults.variadic) {
                    sb.append("...")
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
                sb.append(": ")

                val paramTy = it.ty ?: Ty.UNKNOWN
                if (paramTy is TyGenericParameter && paramTy.superClass != null) {
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
            if (it is TyUnion || (it is TyGenericParameter && it.superClass != null)) {
                sb.append("(")
                render(it, sb)
                sb.append(")")
            } else {
                render(it, sb)
            }
        }
    }

    open fun renderParamsList(params: Collection<String>?): String {
        return if (params != null && params.isNotEmpty()) "<${params.joinToString(", ")}>" else ""
    }

    open fun renderClass(clazz: ITyClass): String {
        return when {
            clazz is TyDocTable -> {
                val context = SearchContext.get(clazz.table.project)
                val list = mutableListOf<String>()
                clazz.table.tableFieldList.forEach { field ->
                    val key = field.name ?: "[${render(field.guessIndexType(context) ?: Ty.VOID)}]"
                    field.valueType?.let { fieldValue ->
                        val fieldTy = fieldValue.getType()
                        val renderedFieldTy = (fieldTy as? TyGenericParameter)?.varName ?: render(fieldTy)
                        list.add("${key}: ${renderedFieldTy}")
                    }
                }
                "{ ${list.joinToString(", ")} }"
            }
            clazz is TyGenericParameter -> {
                clazz.superClass?.let { "${clazz.varName} : ${it.displayName}" } ?: clazz.varName
            }
            clazz.isAnonymousTable -> renderType(Constants.WORD_TABLE)
            clazz.isAnonymous && !clazz.className.endsWith("#${Constants.WORD_SELF}") -> "[local ${clazz.varName}]"
            clazz.isGlobal -> "[global ${clazz.varName}]"
            else -> "${clazz.className}${renderParamsList(clazz.params?.map { it.toString() })}"
        }
    }

    open fun renderType(t: String): String {
        return t
    }

    companion object {
        val SIMPLE: ITyRenderer = TyRenderer()
    }
}
