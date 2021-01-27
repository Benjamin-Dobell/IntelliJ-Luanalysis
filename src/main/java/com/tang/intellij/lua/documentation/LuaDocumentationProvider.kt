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

package com.tang.intellij.lua.documentation

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.psi.LuaDocTagAlias
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.comment.psi.LuaDocTagField
import com.tang.intellij.lua.editor.completion.LuaDocumentationLookupElement
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.ty.*

/**
 * Documentation support
 * Created by tangzx on 2016/12/10.
 */
class LuaDocumentationProvider : AbstractDocumentationProvider(), DocumentationProvider {

    private val renderer: ITyRenderer = object: TyRenderer() {
        override fun renderType(t: String): String {
            return if (t.isNotEmpty()) buildString { DocumentationManagerUtil.createHyperlink(this, t, t, true) } else t
        }

        override fun renderParamsList(params: Collection<String>?): String {
            return if (params != null && params.isNotEmpty()) "&lt;${params.joinToString(", ")}&gt;" else ""
        }
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element is LuaTypeGuessable) {
            val ty = element.guessType(SearchContext.get(element.project))
            if (ty != null) {
                return buildString {
                    renderTy(this, ty, renderer)
                }
            }
        }
        return super<AbstractDocumentationProvider>.getQuickNavigateInfo(element, originalElement)
    }

    override fun getDocumentationElementForLookupItem(psiManager: PsiManager, obj: Any, element: PsiElement?): PsiElement? {
        if (obj is LuaDocumentationLookupElement) {
            return obj.getDocumentationElement(SearchContext.get(psiManager.project))
        }
        return super<AbstractDocumentationProvider>.getDocumentationElementForLookupItem(psiManager, obj, element)
    }

    override fun getDocumentationElementForLink(psiManager: PsiManager, link: String, context: PsiElement?): PsiElement? {
        return LuaClassIndex.find(link, SearchContext.get(psiManager.project))
    }

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        val sb = StringBuilder()
        val tyRenderer = renderer
        when (element) {
            is LuaParamDef -> renderParamDef(sb, element)
            is LuaDocTagAlias -> renderAliasDef(sb, element, tyRenderer)
            is LuaDocTagClass -> renderClassDef(sb, element, tyRenderer)
            is LuaClassMember -> renderClassMember(sb, element)
            is LuaLocalDef -> { //local xx

                renderDefinition(sb) {
                    sb.append("local <b>${element.name}</b>: ")
                    val ty = element.guessType(SearchContext.get(element.project)) ?: Primitives.UNKNOWN
                    renderTy(sb, ty, tyRenderer)
                }

                val owner = PsiTreeUtil.getParentOfType(element, LuaCommentOwner::class.java)
                owner?.let { renderComment(sb, owner.comment, tyRenderer) }
            }
            is LuaLocalFuncDefStat -> {
                sb.wrapTag("pre") {
                    sb.append("local function <b>${element.name}</b>")
                    val type = element.guessType(SearchContext.get(element.project)) as ITyFunction
                    renderSignature(sb, type.mainSignature, tyRenderer)
                }
                renderComment(sb, element.comment, tyRenderer)
            }
        }
        if (sb.isNotEmpty()) return sb.toString()
        return super<AbstractDocumentationProvider>.generateDoc(element, originalElement)
    }

    private fun renderClassMember(sb: StringBuilder, parentTy: ITy?, classMember: LuaClassMember, context: SearchContext): Boolean {
        val effectiveMember = if (parentTy != null) {
            classMember.name?.let {
                parentTy.findEffectiveMember(it, context)
            } ?: classMember.guessIndexType(context)?.let {
                parentTy.findEffectiveIndexer(it, context)
            }
        } else {
            classMember
        }

        if (effectiveMember == null) {
            return false
        }

        val ty = effectiveMember.guessType(context) ?: Primitives.UNKNOWN
        val tyRenderer = renderer

        renderDefinition(sb) {
            //base info
            with(sb) {
                if (ty is ITyFunction) {
                    append("function ")
                }

                if (parentTy != null) {
                    var renderedParentTy = if (parentTy is ITyArray) parentTy.base else parentTy

                    if (renderedParentTy is TySerializedClass && renderedParentTy.className.endsWith(Constants.SUFFIX_CLASS_SELF)) {
                        renderedParentTy = TySerializedClass(getNonSelfTypeName(renderedParentTy))
                    }

                    val parenthesesRequired = renderedParentTy is TyUnion || (renderedParentTy is TyGenericParameter && renderedParentTy.superClass != null)

                    if (parenthesesRequired) {
                        sb.append("(")
                    }

                    renderTy(sb, renderedParentTy, tyRenderer)

                    if (parenthesesRequired) {
                        sb.append(")")
                    }
                }

                val name = classMember.name

                if (name != null) {
                    if (parentTy != null) {
                        if (ty.isColonCall) {
                            append(":")
                        } else {
                            append(".")
                        }
                    }

                    append(name)
                } else {
                    val indexName = classMember.guessIndexType(context)?.displayName ?: "unknown"
                    append("[${indexName}]")
                }

                if (ty is ITyFunction) {
                    renderSignature(sb, ty.mainSignature, tyRenderer)
                } else {
                    sb.append(": ")

                    val parenthesesRequired = ty is TyGenericParameter && ty.superClass != null

                    if (parenthesesRequired) {
                        sb.append("(")
                    }

                    renderTy(sb, ty, tyRenderer)

                    if (parenthesesRequired) {
                        sb.append(")")
                    }
                }

                (classMember as? LuaNameExpr)?.let { nameExpr ->
                    val stat = nameExpr.parent.parent // VAR_LIST ASSIGN_STAT
                    if (stat is LuaAssignStat) renderComment(sb, stat.comment, tyRenderer)
                }
            }
        }

        val commentOwner = classMember as? LuaCommentOwner ?: effectiveMember as? LuaCommentOwner

        if (commentOwner != null) {
            renderComment(sb, commentOwner.comment, tyRenderer)
            return true
        }

        val docTagField = classMember as? LuaDocTagField ?: effectiveMember as? LuaDocTagField

        if (docTagField != null) {
            renderCommentString("  ", null, sb, docTagField.commentString)
            return true
        }

        val assignStat = ((classMember as? LuaIndexExpr)?.parent as? LuaVarList)?.parent as? LuaAssignStat
            ?: ((effectiveMember as? LuaIndexExpr)?.parent as? LuaVarList)?.parent as? LuaAssignStat

        if (assignStat != null) {
            renderComment(sb, assignStat.comment, tyRenderer)
        }

        return true
    }

    private fun renderClassMember(sb: StringBuilder, classMember: LuaClassMember) {
        val context = SearchContext.get(classMember.project)
        val parentType = (classMember.parent as? LuaTableExpr)?.shouldBe(context) ?: classMember.guessClassType(context)

        var memberRendered = false

        if (parentType != null) {
            Ty.eachResolved(parentType, context) {
                memberRendered = renderClassMember(sb, it, classMember, context) || memberRendered
            }
        }

        if (!memberRendered) {
            renderClassMember(sb, null, classMember, context)
        }
    }

    private fun renderParamDef(sb: StringBuilder, paramDef: LuaParamDef) {
        val owner = PsiTreeUtil.getParentOfType(paramDef, LuaCommentOwner::class.java)
        val docParamDef = owner?.comment?.getParamDef(paramDef.name)
        val tyRenderer = renderer
        if (docParamDef != null) {
            renderDocParam(sb, docParamDef, tyRenderer, true)
        } else {
            val ty = infer(paramDef, SearchContext.get(paramDef.project)) ?: Primitives.UNKNOWN
            sb.append("<b>param</b> <code>${paramDef.name}</code> : ")
            renderTy(sb, ty, tyRenderer)
        }
    }
}
