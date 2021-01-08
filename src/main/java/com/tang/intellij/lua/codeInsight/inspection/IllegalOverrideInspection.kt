/*
 * Copyright (c) 2020
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

package com.tang.intellij.lua.codeInsight.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElementVisitor
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.comment.psi.LuaDocTagField
import com.tang.intellij.lua.comment.psi.LuaDocVisitor
import com.tang.intellij.lua.comment.psi.api.LuaComment
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.ITy
import com.tang.intellij.lua.ty.ProblemUtil
import com.tang.intellij.lua.ty.Ty
import com.tang.intellij.lua.ty.TyVarianceFlags

class IllegalOverrideInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : LuaVisitor() {
            private fun inspectMember(context: SearchContext, superTy: ITy, member: LuaClassMember, sourceTy: ITy, source: LuaPsiElement) {
                val indexTy = member.guessIndexType(context)
                val superMemberTy = if (indexTy != null) {
                    superTy.guessIndexerType(indexTy, context, true)
                } else {
                    member.name?.let { superTy.guessMemberType(it, context) }
                }

                if (superMemberTy != null) {
                    val varianceFlags = if (source is LuaTableExpr) TyVarianceFlags.WIDEN_TABLES else 0
                    val fieldIdentifier = member.name?.let { "\"${it}\"" } ?: "[${indexTy!!.displayName}]"

                    ProblemUtil.contravariantOf(superMemberTy, sourceTy, context, varianceFlags, null, source) { problem ->
                        val message = "Illegal override of ${fieldIdentifier}. ${problem.message}"
                        val highlightType = problem.highlightType ?: ProblemHighlightType.ERROR

                        holder.registerProblem(problem.sourceElement, message, highlightType)
                    }
                }
            }

            private fun inspectClassTableField(context: SearchContext, superTy: ITy, tableField: LuaTableField) {
                tableField.valueExpr?.let { valueExpr ->
                    if (tableField.isExplicitlyTyped) {
                        val docType = tableField.comment?.tagType?.typeList?.tyList?.firstOrNull()

                        if (docType != null) {
                            inspectMember(context, superTy, tableField, docType.getType(), docType)
                        }
                    } else {
                        inspectMember(context, superTy, tableField, valueExpr.guessType(context) ?: Ty.UNKNOWN, valueExpr)
                    }
                }
            }

            fun visitClassTableExpr(tagClass: LuaDocTagClass, tableExpr: LuaTableExpr?) {
                if (tableExpr == null) {
                    return
                }

                val superClassRef = tagClass.superClassRef

                if (superClassRef == null) {
                    return
                }

                val context = SearchContext.get(tableExpr.project)
                val superTy = superClassRef.typeRef.resolveType(context)

                tableExpr.tableFieldList.forEach {
                    inspectClassTableField(context, superTy, it)
                }
            }

            override fun visitAssignStat(o: LuaAssignStat) {
                if (o.varExprList.expressionList.size == 1) {
                    val tagClass = o.comment?.tagClass

                    if (tagClass != null) {
                        visitClassTableExpr(tagClass, o.varExprList.expressionList.first() as? LuaTableExpr)
                    }
                }
            }

            override fun visitLocalDefStat(o: LuaLocalDefStat) {
                val expressionList = o.exprList?.expressionList

                if (expressionList?.size == 1) {
                    val tagClass = o.comment?.tagClass

                    if (tagClass != null) {
                        visitClassTableExpr(tagClass, expressionList.first() as? LuaTableExpr)
                    }
                }
            }

            override fun visitClassMethodDefStat(o: LuaClassMethodDefStat) {
                super.visitClassMethodDefStat(o)

                val context = SearchContext.get(o.project)
                val superTy = o.guessParentType(context).getSuperClass(context)

                if (superTy == null) {
                    return
                }

                if (o.isExplicitlyTyped) {
                    val docType = o.comment?.tagType?.typeList?.tyList?.firstOrNull()

                    if (docType != null) {
                        inspectMember(context, superTy, o, docType.getType(), docType)
                        return
                    } else {
                        // TODO: Inspect params and return ty directly
                    }
                }

                o.guessType(context)?.let { memberTy ->
                    inspectMember(context, superTy, o, memberTy, o)
                }
            }

            override fun visitComment(o: PsiComment) {
                val cls = (o as? LuaComment)?.tagClass?.type

                if (cls == null) {
                    return
                }

                val context = SearchContext.get(o.project)
                val superTy = cls.getSuperClass(context)

                if (superTy == null) {
                    return
                }

                o.acceptChildren(object : LuaDocVisitor() {
                    override fun visitTagField(o: LuaDocTagField) {
                        val type = o.valueType

                        if (type != null) {
                            inspectMember(context, superTy, o, type.getType(), type)
                        }
                    }
                })
            }
        }
    }
}
