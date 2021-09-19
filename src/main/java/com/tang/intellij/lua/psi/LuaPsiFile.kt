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

package com.tang.intellij.lua.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.testFramework.LightVirtualFile
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.psi.api.LuaComment
import com.tang.intellij.lua.lang.LuaFileType
import com.tang.intellij.lua.lang.LuaLanguage
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.LuaFileStub

/**
 * Created by tangzx on 2015/11/15.
 * Email:love.tangzx@qq.com
 */
open class LuaPsiFile(fileViewProvider: FileViewProvider) : PsiFileBase(fileViewProvider, LuaLanguage.INSTANCE), LuaPsiTypeGuessable, LuaDeclarationScope {

    val identifier: String by lazy {
        val virtualFile = viewProvider.virtualFile
        val originalFile = (virtualFile as? LightVirtualFile)?.originalFile ?: virtualFile
        originalFile.url
    }

    override fun getFileType(): FileType {
        return LuaFileType.INSTANCE
    }

    val tooLarger: Boolean get() {
        val fileLimit = LuaSettings.instance.tooLargerFileThreshold * 1024
        val fileSize = viewProvider.virtualFile.length
        return fileSize > fileLimit
    }

    override fun setName(name: String): PsiElement {
        return if (FileUtil.getNameWithoutExtension(name) == name) {
            super.setName("$name.${LuaFileType.INSTANCE.defaultExtension}")
        } else super.setName(name)
    }

    fun getModuleName(context: SearchContext): String? {
        val stub = if (!context.isDumb) stub as? LuaFileStub else null
        return if (stub != null) stub.module else findCachedModuleName()
    }

    /**
     * Lua language version
     */
    val languageLevel get() = LuaSettings.instance.languageLevel

    private fun findCachedModuleName(): String? {
        return CachedValuesManager.getCachedValue(this, KEY_CACHED_MODULE_NAME) {
            CachedValueProvider.Result.create(findModuleName(), this)
        }
    }

    private fun findModuleName():String? {
        var child: PsiElement? = firstChild
        while (child != null) {
            if (child is LuaComment) { // ---@module name
                val name = child.moduleName
                if (name != null) return name
            } else if (child is LuaStatement) {
                val comment = child.comment
                if (comment != null) {
                    val name = comment.moduleName
                    if (name != null) return name
                }
                if (child is LuaExprStat) {
                    val exprComment = child.expression.comment
                    if (exprComment != null) {
                        val name = exprComment.moduleName
                        if (name != null) return name
                    }
                    val callExpr = child.expression as? LuaCallExpr
                    val expr = callExpr?.expression
                    if (expr is LuaNameExpr && expr.textMatches(Constants.WORD_MODULE)) { // module("name")
                        val stringArg = callExpr.firstStringArg
                        if (stringArg != null)
                            return stringArg.text
                    }
                }
            }
            child = child.nextSibling
        }
        return null
    }

    companion object {
        private val KEY_CACHED_MODULE_NAME = Key.create<CachedValue<String?>>("lua.file.module.name")
    }
}

fun PsiFile.getFileIdentifier(): String {
    return (this as? LuaPsiFile)?.identifier ?: this.name
}
