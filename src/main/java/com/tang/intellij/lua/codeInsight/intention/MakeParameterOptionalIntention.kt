/*
 * Copyright (c) 2023
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

package com.tang.intellij.lua.codeInsight.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.tang.intellij.lua.comment.psi.LuaDocFunctionParam
import com.tang.intellij.lua.comment.psi.LuaDocPsiElement
import com.tang.intellij.lua.comment.psi.LuaDocTagParam
import com.tang.intellij.lua.psi.LuaElementFactory
import com.tang.intellij.lua.psi.LuaPsiTreeUtil

class MakeParameterOptionalIntention : BaseIntentionAction() {
    init {
        text = "Make parameter optional"
    }

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean {
        if (psiFile == null || editor == null) {
            return false
        }

        var docPsi = LuaPsiTreeUtil.findElementOfClassAtOffset(psiFile, editor.caretModel.offset, LuaDocPsiElement::class.java, false)

        while (docPsi != null) {
            if (docPsi is LuaDocFunctionParam) {
                return docPsi.optional == null && docPsi.ty != null
            }

            if (docPsi is LuaDocTagParam) {
                return docPsi.optional == null && docPsi.ty != null && docPsi.paramNameRef != null
            }

            docPsi = docPsi.parent as? LuaDocPsiElement
        }

        return false
    }

    override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
        if (psiFile == null || editor == null) {
            return
        }

        var docPsi = LuaPsiTreeUtil.findElementOfClassAtOffset(psiFile, editor.caretModel.offset, LuaDocPsiElement::class.java, false)

        while (docPsi != null) {
            if (docPsi is LuaDocFunctionParam) {
                val type = docPsi.ty?.text

                if (docPsi.optional == null && type != null) {
                    docPsi.replace(LuaElementFactory.createDocFunctionParam(project, docPsi.id.text, type, true))
                }

                return
            }

            if (docPsi is LuaDocTagParam) {
                val name = docPsi.paramNameRef?.id?.text
                val type = docPsi.ty?.text

                if (docPsi.optional == null && name != null && type != null) {
                    docPsi.replace(LuaElementFactory.createDocTagParam(project, name, type, true, docPsi.commentString?.text))
                }

                return
            }

            docPsi = docPsi.parent as? LuaDocPsiElement
        }
    }
}
