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

package com.tang.intellij.lua.reference

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.tang.intellij.lua.lang.type.LuaString
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.returnStatement

/**
 *
 * Created by tangzx on 2016/12/9.
 */
class LuaRequireReference internal constructor(callExpr: LuaCallExpr) : PsiReferenceBase<LuaCallExpr>(callExpr) {

    private var pathString: String? = null
    private var range = TextRange.EMPTY_RANGE
    private val path: PsiElement? = callExpr.firstStringArg
    private var quot: String = "\""

    init {
        if (path != null && path.textLength > 2) {
            val text = path.text
            val luaString = LuaString.getContent(text)
            pathString = luaString.value
            quot = text.substring(0, luaString.start)

            if (pathString != null) {
                val start = path.textOffset - callExpr.textOffset + luaString.start
                val end = start + pathString!!.length
                range = TextRange(start, end)
            }
        }
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        val resolved = if (element is LuaPsiFile) {
            resolveRequireFile(pathString, myElement.project)
        } else resolve()
        return myElement.manager.areElementsEquivalent(element, resolved)
    }

    override fun getRangeInElement(): TextRange {
        return range
    }

    override fun resolve(): PsiElement? {
        if (pathString == null) {
            return null
        }

        val filePsi = resolveRequireFile(pathString, myElement.project)

        if (filePsi != null) {
            val returnStatement = filePsi.returnStatement()

            if (returnStatement != null && returnStatement.exprList?.expressionList?.size == 1) {
                val resolvedNameExpr = returnStatement.exprList!!.expressionList.first() as? LuaNameExpr

                return if (resolvedNameExpr != null) {
                    resolveInFile(SearchContext.get(myElement.project), resolvedNameExpr.name, resolvedNameExpr)
                } else returnStatement
            }

            return returnStatement
        }

        return filePsi
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        pathString?.let {
            val name = FileUtil.getNameWithoutExtension(newElementName)
            val last = it.lastIndexOf('.')
            val path = if (last == -1) name else it.substring(0, last) + "." + name
            setPath(path)
        }
        return myElement
    }

    fun setPath(luaPath: String) {
        if (path != null) {
            val stat = LuaElementFactory.createWith(myElement.project, "require $quot$luaPath$quot") as LuaExprStat
            val stringArg = (stat.expression as? LuaCallExpr)?.firstStringArg
            if (stringArg != null)
                path.replace(stringArg)
        }
    }

    override fun bindToElement(element: PsiElement): PsiElement? {
        return null
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
