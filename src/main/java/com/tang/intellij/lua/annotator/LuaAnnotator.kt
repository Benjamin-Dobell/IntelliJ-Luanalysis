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

package com.tang.intellij.lua.annotator

import com.intellij.lang.annotation.*
import com.intellij.lang.annotation.Annotation
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.psi.*
import com.tang.intellij.lua.highlighting.LuaHighlightingData
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext

/**
 * LuaAnnotator
 * Created by TangZX on 2016/11/22.
 */
class LuaAnnotator : Annotator {
    private var myHolder: AnnotationHolder? = null
    private val luaVisitor = LuaElementVisitor()
    private val docVisitor = LuaDocElementVisitor()
    private var isModuleFile: Boolean = false

    companion object {
        private val STD_MARKER = Key.create<Boolean>("lua.std.marker")
    }

    override fun annotate(psiElement: PsiElement, annotationHolder: AnnotationHolder) {
        myHolder = annotationHolder
        if (psiElement is LuaDocPsiElement) {
            psiElement.accept(docVisitor)
        } else if (psiElement is LuaPsiElement) {
            val psiFile = psiElement.containingFile
            isModuleFile = (psiFile as? LuaPsiFile)?.let {
                psiFile.getModuleName(SearchContext.get(psiFile.project))
            } != null
            psiElement.accept(luaVisitor)
        }
        myHolder = null
    }

    private fun createInfoAnnotation(psi: PsiElement, msg: String? = null): AnnotationBuilder {
        val builder = if (msg != null) {
            myHolder!!.newAnnotation(HighlightSeverity.INFORMATION, msg)
        } else {
            myHolder!!.newSilentAnnotation(HighlightSeverity.INFORMATION)
        }

        return builder.range(psi)
    }

    internal inner class LuaElementVisitor : LuaVisitor() {

        override fun visitExprStat(o: LuaExprStat) {
            if (o.expr !is LuaCallExpr) {
                if (o.containingFile !is LuaExprCodeFragment) {
                    myHolder!!.newAnnotation(HighlightSeverity.ERROR, "syntax error").range(o).create()
                }
            } else {
                super.visitExprStat(o)
            }
        }

        override fun visitLocalFuncDef(o: LuaLocalFuncDef) {
            val name = o.nameIdentifier

            if (name != null) {
                createInfoAnnotation(name, "Local function \"${o.name}\"")
                        .textAttributes(LuaHighlightingData.LOCAL_VAR)
                        .create()
            }
        }

        override fun visitLocalDef(o: LuaLocalDef) {
            val nameList = o.nameList
            if (nameList != null) {
                var child: PsiElement? = nameList.firstChild
                while (child != null) {
                    if (child is LuaNameDef) {
                        createInfoAnnotation(child, "Local variable \"${child.name}\"")
                                .textAttributes(LuaHighlightingData.LOCAL_VAR)
                                .create()
                    }
                    child = child.nextSibling
                }
            }
            super.visitLocalDef(o)
        }

        override fun visitTableField(o: LuaTableField) {
            super.visitTableField(o)
            val id = o.id
            if (id != null) {
                createInfoAnnotation(id)
                        .textAttributes(LuaHighlightingData.FIELD)
                        .create()
            }
        }

        override fun visitFuncDef(o: LuaFuncDef) {
            val name = o.nameIdentifier ?: return

            if (isModuleFile) {
                createInfoAnnotation(name, "Module function \"${o.name}\"")
                        .textAttributes(LuaHighlightingData.INSTANCE_METHOD)
                        .create()
            } else {
                createInfoAnnotation(name, "Global function \"${o.name}\"")
                        .textAttributes(LuaHighlightingData.GLOBAL_FUNCTION)
                        .create()
            }
        }

        override fun visitClassMethodName(o: LuaClassMethodName) {
            val id = o.id ?: return
            val textAttributes = if (o.dot != null) {
                LuaHighlightingData.STATIC_METHOD
            } else {
                LuaHighlightingData.INSTANCE_METHOD
            }
            createInfoAnnotation(id)
                    .textAttributes(textAttributes)
                    .create()
        }

        override fun visitParamNameDef(o: LuaParamNameDef) {
            createInfoAnnotation(o, "Parameter : \"${o.name}\"")
                    .textAttributes(LuaHighlightingData.PARAMETER)
                    .create()
        }

        override fun visitNameExpr(o: LuaNameExpr) {
            val id = o.id

            val context = SearchContext.get(o.project)
            val res = resolve(o, context)

            if (res != null) { //std api highlighting
                val containingFile = res.containingFile
                if (LuaFileUtil.isStdLibFile(containingFile.virtualFile, o.project)) {
                    createInfoAnnotation(id, "Std apis")
                            .textAttributes(LuaHighlightingData.STD_API)
                            .create()
                    o.putUserData(STD_MARKER, true)
                    return
                }
            }

            if (res is LuaParamNameDef) {
                if (!checkUpValue(o)) {
                    createInfoAnnotation(id, "Parameter : \"${res.name}\"")
                            .textAttributes(LuaHighlightingData.PARAMETER)
                            .create()
                }
            } else if (res is LuaFuncDef) {
                val resolvedFile = res.containingFile
                if (resolvedFile !is LuaPsiFile || resolvedFile.getModuleName(context) == null) {
                    createInfoAnnotation(id, "Global function : \"${res.name}\"")
                            .textAttributes(LuaHighlightingData.GLOBAL_FUNCTION)
                            .create()
                } else {
                    createInfoAnnotation(id, "Module function : \"${res.name}\"")
                }
            } else {
                if (id.textMatches(Constants.WORD_SELF)) {
                    if (!checkUpValue(o)) {
                        createInfoAnnotation(id)
                                .textAttributes(LuaHighlightingData.SELF)
                                .create()
                    }
                } else if (res is LuaNameDef) {
                    if (!checkUpValue(o)) {
                        createInfoAnnotation(id, "Local variable \"${o.name}\"")
                                .textAttributes(LuaHighlightingData.LOCAL_VAR)
                                .create()
                    }
                } else if (res is LuaLocalFuncDef) {
                    if (!checkUpValue(o)) {
                        createInfoAnnotation(id, "Local function \"${o.name}\"")
                                .textAttributes(LuaHighlightingData.LOCAL_VAR)
                                .create()
                    }
                } else {
                    if (isModuleFile) {
                        createInfoAnnotation(id, "Module field \"${o.name}\"")
                                .textAttributes(LuaHighlightingData.FIELD)
                                .create()
                    } else {
                        createInfoAnnotation(id, "Global variable \"${o.name}\"")
                                .textAttributes(LuaHighlightingData.GLOBAL_VAR)
                                .create()
                    }
                }
            }
        }

        private fun checkUpValue(o: LuaNameExpr): Boolean {
            val upValue = isUpValue(o, SearchContext.get(o.project))
            if (upValue) {
                myHolder?.newAnnotation(HighlightSeverity.INFORMATION, "Up-value \"${o.name}\"")
                        ?.range(o.id.textRange)
                        ?.textAttributes(LuaHighlightingData.UP_VALUE)
                        ?.create()
            }
            return upValue
        }

        override fun visitIndexExpr(o: LuaIndexExpr) {
            super.visitIndexExpr(o)
            val prefix = o.prefixExpr
            if (prefix is LuaNameExpr && prefix.getUserData(STD_MARKER) != null) {
                createInfoAnnotation(o, "Std apis")
                        .textAttributes(LuaHighlightingData.STD_API)
                        .create()
                o.putUserData(STD_MARKER, true)
            } else {
                val id = o.id
                if (id != null) {
                    val builder = createInfoAnnotation(id, null)
                    if (o.parent is LuaCallExpr) {
                        if (o.colon != null) {
                            builder.textAttributes(LuaHighlightingData.INSTANCE_METHOD)
                        } else {
                            builder.textAttributes(LuaHighlightingData.STATIC_METHOD)
                        }
                    } else {
                        if (o.colon != null) {
                            myHolder!!.newAnnotation(HighlightSeverity.ERROR, "Arguments expected")
                                    .range(o)
                                    .create()
                        } else {
                            builder.textAttributes(LuaHighlightingData.FIELD)
                        }
                    }
                    builder.create()
                }
            }
        }
    }

    internal inner class LuaDocElementVisitor : LuaDocVisitor() {
        override fun visitTagClass(o: LuaDocTagClass) {
            super.visitTagClass(o)
            createInfoAnnotation(o.id, null)
                    .textAttributes(LuaHighlightingData.CLASS_NAME)
                    .create()
        }

        override fun visitTagAlias(o: LuaDocTagAlias) {
            super.visitTagAlias(o)
            val id = o.id
            createInfoAnnotation(id, null)
                    .textAttributes(LuaHighlightingData.TYPE_ALIAS)
                    .create()
        }

        override fun visitClassNameRef(o: LuaDocClassNameRef) {
            createInfoAnnotation(o, null)
                    .textAttributes(LuaHighlightingData.CLASS_REFERENCE)
                    .create()
        }

        override fun visitTagField(o: LuaDocTagField) {
            super.visitTagField(o)
            val id = o.nameIdentifier
            if (id != null) {
                createInfoAnnotation(id, null)
                        .textAttributes(LuaHighlightingData.DOC_COMMENT_TAG_VALUE)
                        .create()
            }
        }

        override fun visitParamNameRef(o: LuaDocParamNameRef) {
            createInfoAnnotation(o, null)
                    .textAttributes(LuaHighlightingData.DOC_COMMENT_TAG_VALUE)
                    .create()
        }
    }
}
