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
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.PsiSearchContext
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*

class AssignTypeInspection : StrictInspection() {
    override fun buildVisitor(myHolder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
            object : LuaVisitor() {
                private fun unionAwareProblemProcessor(ownerTy: ITy, targetTy: ITy, context: SearchContext, processProblem: ProcessProblem): ProcessProblem {
                    if (!TyUnion.isUnion(ownerTy, context)) {
                        return processProblem
                    }

                    return { targetElement: PsiElement?, sourceElement: PsiElement, message: String, highlightType: ProblemHighlightType? ->
                        val tyMessage = "${message} on union member ${targetTy.displayName}"
                        processProblem(targetElement, sourceElement, tyMessage, highlightType)
                    }
                }

                private fun inspectAssignee(assignee: LuaTypeGuessable, value: ITy, varianceFlags: Int, targetElement: PsiElement?, expressionElement: LuaExpression<*>, context: SearchContext, processProblem: ProcessProblem) {
                    if (assignee is LuaIndexExpr) {
                        // Get owner class
                        val fieldOwnerType = assignee.guessParentType(context)

                        if (fieldOwnerType is TyTable && value is TyTable && fieldOwnerType.table == value.table) {
                            return
                        }

                        Ty.eachResolved(fieldOwnerType, context) { targetTy ->
                            // table<K, V> will always accept nil value assignment i.e. entry removal
                            val sourceTy = if (targetTy is ITyGeneric && targetTy.base == Ty.TABLE) {
                                if (value == Ty.NIL) {
                                    return
                                }
                                if (value is TyUnion) {
                                    value.getChildTypes().fold(Ty.VOID as ITy) { acc, ty ->
                                        if (ty == Ty.NIL) acc else acc.union(ty, context)
                                    }
                                } else {
                                    value
                                }
                            } else value

                            val idExpr = assignee.idExpr
                            val memberName = assignee.name

                            val targetMemberType = if (memberName != null) {
                                targetTy.guessMemberType(memberName, context)
                            } else {
                                idExpr?.guessType(context)?.let {
                                    targetTy.guessIndexerType(it, context)
                                }
                            }

                            if (targetMemberType != null) {
                                val processor = unionAwareProblemProcessor(fieldOwnerType, targetTy, context, processProblem)
                                val flags = if (targetTy is ITyArray) varianceFlags or TyVarianceFlags.STRICT_NIL else varianceFlags
                                ProblemUtil.contravariantOf(targetMemberType, sourceTy, context, flags, targetElement, expressionElement, processor)
                            }
                        }
                    } else {
                        if (assignee is LuaNameExpr) {
                            val tree = LuaDeclarationTree.get(assignee.containingFile)
                            (tree.find(assignee)?.firstDeclaration?.psi as? LuaLocalDef)?.let {
                                if (it.const != null || it.close != null) {
                                    processProblem(null, assignee, "Attempt to assign to const variable", ProblemHighlightType.ERROR)
                                }
                            }
                        }

                        val variableType = assignee.guessType(context)

                        if (variableType == null || (variableType is TyTable && value is TyTable && variableType.table == value.table)) {
                            return
                        }

                        ProblemUtil.contravariantOf(variableType, value, context, varianceFlags, targetElement, expressionElement, processProblem)
                    }
                }

                fun inspectAssignment(assignees: List<LuaTypeGuessable>, expressions: List<LuaExpression<*>>?) {
                    if (expressions == null || expressions.size == 0) {
                        return
                    }

                    val searchContext = PsiSearchContext(expressions.first())
                    var assigneeIndex = 0
                    var variadicTy: ITy? = null
                    var lastExpressionFirstAssigneeIndex = 0

                    for (expressionIndex in 0 until expressions.size) {
                        val isLastExpression = expressionIndex == expressions.size - 1
                        val expression = expressions[expressionIndex]
                        val expressionType = if (isLastExpression) {
                            searchContext.withMultipleResults { expression.guessType(searchContext) }
                        } else {
                            searchContext.withIndex(0) { expression.guessType(searchContext) }
                        }

                        if (expressionType == null) {
                            return
                        }

                        val varianceFlags = if (expression is LuaTableExpr) TyVarianceFlags.WIDEN_TABLES else 0

                        var multipleResults = expressionType as? TyMultipleResults
                        var values = if (multipleResults != null) multipleResults.list else listOf(expressionType)
                        var valueIndex = 0

                        if (isLastExpression) {
                            lastExpressionFirstAssigneeIndex = assigneeIndex
                        }

                        while (valueIndex < values.size) {
                            var value = values[valueIndex]
                            val isLastValue = valueIndex == values.lastIndex

                            if (isLastValue) {
                                if (variadicTy == null && isLastExpression && multipleResults?.variadic == true) {
                                    variadicTy = if (value is TyMultipleResults) {
                                        TyMultipleResults.getResult(searchContext, value)
                                    } else value

                                    if (LuaSettings.instance.isNilStrict) {
                                        variadicTy = Ty.NIL.union(variadicTy, searchContext)
                                    }
                                }

                                // Nested multiple value handling. Particularly important for handling generic parameter substitutions with multiple results.
                                if (value is TyMultipleResults) {
                                    multipleResults = value
                                    values = value.list
                                    valueIndex = 0
                                    continue
                                }
                            }

                            if (assigneeIndex >= assignees.size) {
                                if (!isLastValue || multipleResults?.variadic != true) {
                                    for (i in expressionIndex until expressions.size) {
                                        myHolder.registerProblem(expressions[i], "Insufficient assignees, values will be discarded.", ProblemHighlightType.WEAK_WARNING)
                                    }
                                }
                                return
                            }

                            val assignee = assignees[assigneeIndex++]

                            if (variadicTy != null) {
                                variadicTy = variadicTy.union(value, searchContext)
                                value = variadicTy
                            }

                            if (assignees.size == 1 && LuaPsiTreeUtil.findAncestorOfType(assignee, LuaStatement::class.java)?.comment?.tagClass != null) {
                                if (value !is TyTable) {
                                    myHolder.registerProblem(expression, "Type mismatch. Required: 'table' Found: '%s'".format(value.displayName))
                                }
                            } else {
                                val inspectionTargetElement = if (assignees.size > 1) assignee else null
                                inspectAssignee(assignee, value, varianceFlags, inspectionTargetElement, expression, searchContext) { targetElement, sourceElement, message, highlightType ->
                                    val sourceMessage = if (assignees.size > 1 && values.size > 1) "Result ${valueIndex + 1}, ${message.decapitalize()}" else message
                                    myHolder.registerProblem(sourceElement, sourceMessage, highlightType ?: ProblemHighlightType.GENERIC_ERROR_OR_WARNING)

                                    if (targetElement != null && targetElement != sourceElement) {
                                        myHolder.registerProblem(targetElement, message, highlightType ?: ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                                    }
                                }
                            }

                            if (!isLastExpression) {
                                break // Multiple values are only handled for the last expression
                            }

                            valueIndex++
                        }
                    }

                    if (assigneeIndex < assignees.size) {
                        while (assigneeIndex < assignees.size) {
                            if (variadicTy != null) {
                                val assignee = assignees[assigneeIndex]

                                inspectAssignee(assignee, variadicTy, 0, assignee, expressions.last(), searchContext) { targetElement, sourceElement, message, highlightType ->
                                    val resultIndex = assigneeIndex - lastExpressionFirstAssigneeIndex + 1
                                    val sourceMessage = if (assignees.size > 1) "Result ${resultIndex}, ${message.decapitalize()}" else message
                                    myHolder.registerProblem(sourceElement, sourceMessage, highlightType ?: ProblemHighlightType.GENERIC_ERROR_OR_WARNING)

                                    if (targetElement != null && targetElement != sourceElement) {
                                        myHolder.registerProblem(targetElement, message, highlightType ?: ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                                    }
                                }

                                assigneeIndex++
                            } else {
                                myHolder.registerProblem(assignees[assigneeIndex++], "Too many assignees, will be assigned nil.")
                            }
                        }
                    }
                }

                override fun visitAssignStat(o: LuaAssignStat) {
                    super.visitAssignStat(o)
                    inspectAssignment(o.varExprList.expressionList, o.valueExprList?.expressionList)
                }

                override fun visitLocalDefStat(o: LuaLocalDefStat) {
                    super.visitLocalDefStat(o)
                    inspectAssignment(o.localDefList, o.exprList?.expressionList)
                }
            }
}
