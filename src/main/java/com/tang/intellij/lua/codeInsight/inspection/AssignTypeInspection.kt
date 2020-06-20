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

package com.tang.intellij.lua.codeInsight.inspection

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*

class AssignTypeInspection : StrictInspection() {
    override fun buildVisitor(myHolder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
            object : LuaVisitor() {
                private fun unionAwareProblemProcessor(ownerTy: ITy, targetTy: ITy, processProblem: ProcessProblem): ProcessProblem {
                    if (ownerTy !is TyUnion) {
                        return processProblem
                    }

                    return { targetElement: PsiElement?, sourceElement: PsiElement, message: String, highlightType: ProblemHighlightType ->
                        val tyMessage = "${message} on union member ${targetTy.displayName}"
                        processProblem(targetElement, sourceElement, tyMessage, highlightType)
                    }
                }

                private fun inspectAssignee(assignee: LuaTypeGuessable, value: ITy, varianceFlags: Int, targetElement: PsiElement?, expressionElement: LuaExpr, context: SearchContext, processProblem: ProcessProblem) {
                    if (assignee is LuaIndexExpr) {
                        // Get owner class
                        val fieldOwnerType = TyAliasSubstitutor.substitute(assignee.guessParentType(context), context)

                        TyUnion.each(fieldOwnerType) { targetTy ->
                            // table<K, V> will always accept nil value assignment i.e. entry removal
                            val sourceTy = if (targetTy is ITyGeneric && targetTy.base == Ty.TABLE) {
                                if (value == Ty.NIL) {
                                    return
                                }
                                if (value is TyUnion) value.getChildTypes().fold(Ty.VOID as ITy) { acc, ty ->
                                    if (ty == Ty.NIL) acc else acc.union(ty)
                                } else {
                                    value
                                }
                            } else value

                            val idExpr = assignee.idExpr
                            val memberName = assignee.name

                            val targetMemberType = if (memberName != null) {
                                targetTy.guessMemberType(memberName, context)
                            } else {
                                idExpr?.let { targetTy.guessIndexerType(it.guessType(context), context) }
                            } ?: Ty.NIL

                            val processor = unionAwareProblemProcessor(fieldOwnerType, targetTy, processProblem)
                            val flags = if (targetTy is ITyArray) varianceFlags or TyVarianceFlags.STRICT_NIL else varianceFlags
                            ProblemUtil.contravariantOf(targetMemberType, sourceTy, context, flags, targetElement, expressionElement, processor)
                        }
                    } else {
                        val variableType = assignee.guessType(context)
                        ProblemUtil.contravariantOf(variableType, value, context, varianceFlags, targetElement, expressionElement, processProblem)
                    }
                }

                fun inspectAssignment(assignees: List<LuaTypeGuessable>, expressions: List<LuaExpr>?) {
                    if (expressions == null || expressions.size == 0) {
                        return
                    }

                    val searchContext = SearchContext.get(expressions.first().project)
                    var assigneeIndex = 0
                    var variadicMultipleResults: TyMultipleResults? = null

                    for (expressionIndex in 0 until expressions.size) {
                        val isLastExpression = expressionIndex == expressions.size - 1
                        val expression = expressions[expressionIndex]
                        val expressionType = if (isLastExpression) {
                            searchContext.withMultipleResults { expression.guessType(searchContext) }
                        } else {
                            searchContext.withIndex(0) { expression.guessType(searchContext) }
                        }
                        val varianceFlags = if (expression is LuaTableExpr) TyVarianceFlags.WIDEN_TABLES else 0
                        val multipleResults = expressionType as? TyMultipleResults
                        val values = if (multipleResults != null) multipleResults.list else listOf(expressionType)

                        for (valueIndex in 0 until values.size) {
                            val value = values[valueIndex]
                            val variadic = isLastExpression && valueIndex == values.size - 1 && multipleResults?.variadic == true

                            if (variadic) {
                                variadicMultipleResults = multipleResults
                            }

                            if (assigneeIndex >= assignees.size) {
                                if (!variadic) {
                                    for (i in expressionIndex until expressions.size) {
                                        myHolder.registerProblem(expressions[i], "Insufficient assignees, values will be discarded.", ProblemHighlightType.WEAK_WARNING)
                                    }
                                }
                                return
                            }

                            val assignee = assignees[assigneeIndex++]

                            if (assignees.size == 1 && (assignee.parent?.parent as? LuaCommentOwner)?.comment?.tagClass != null) {
                                if (value !is TyTable) {
                                    myHolder.registerProblem(expression, "Type mismatch. Required: 'table' Found: '%s'".format(value.displayName))
                                }
                            } else {
                                val inspectionTargetElement = if (assignees.size > 1) assignee else null
                                inspectAssignee(assignee, value, varianceFlags, inspectionTargetElement, expression, searchContext) { targetElement, sourceElement, message, highlightType ->
                                    val sourceMessage = if (assignees.size > 1 && (variadic || values.size > 1)) "Result ${valueIndex + 1}, ${message.decapitalize()}" else message
                                    myHolder.registerProblem(sourceElement, sourceMessage, highlightType)

                                    if (targetElement != null && targetElement != sourceElement) {
                                        myHolder.registerProblem(targetElement, message, highlightType)
                                    }
                                }
                            }

                            if (!isLastExpression) {
                                break // Multiple values are only handled for the last expression
                            }
                        }
                    }

                    if (assigneeIndex < assignees.size) {
                        val lastExplicitIndex = assigneeIndex
                        for (i in lastExplicitIndex until assignees.size) {
                            if (variadicMultipleResults != null) {
                                val assignee = assignees[assigneeIndex++]
                                inspectAssignee(assignee, variadicMultipleResults.list.last(), 0, assignee, expressions.last(), searchContext) { targetElement, sourceElement, message, highlightType ->
                                    val resultIndex = variadicMultipleResults.list.size + assigneeIndex - lastExplicitIndex
                                    val sourceMessage = if (assignees.size > 1) "Result ${resultIndex}, ${message.decapitalize()}" else message
                                    myHolder.registerProblem(sourceElement, sourceMessage, highlightType)

                                    if (targetElement != null && targetElement != sourceElement) {
                                        myHolder.registerProblem(targetElement, message, highlightType)
                                    }
                                }
                            } else {
                                myHolder.registerProblem(assignees[i], "Too many assignees, will be assigned nil.")
                            }
                        }
                    }
                }

                override fun visitAssignStat(o: LuaAssignStat) {
                    super.visitAssignStat(o)
                    inspectAssignment(o.varExprList.exprList, o.valueExprList?.exprList)
                }

                override fun visitLocalDef(o: LuaLocalDef) {
                    super.visitLocalDef(o)
                    val nameList = o.nameList

                    if (nameList != null) {
                        inspectAssignment(nameList.nameDefList, o.exprList?.exprList)
                    }
                }
            }
}
