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
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.comment.psi.impl.LuaDocTagTypeImpl
import com.tang.intellij.lua.lang.LuaFileType.DEFINITION_FILE_REGEX
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.PsiSearchContext
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*

class ReturnTypeInspection : StrictInspection() {
    override fun buildVisitor(myHolder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        if (session.file.name.matches(DEFINITION_FILE_REGEX)) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : LuaVisitor() {
            override fun visitReturnStat(o: LuaReturnStat) {
                super.visitReturnStat(o)
                if (o.parent is PsiFile)
                    return

                val context = PsiSearchContext(o)
                val bodyOwner = PsiTreeUtil.getParentOfType(o, LuaFuncBodyOwner::class.java) ?: return
                val functionReturnDocTy = if (bodyOwner is LuaClassMethodDefStat) {
                    guessSuperReturnTypes(bodyOwner, context)
                } else {
                    bodyOwner.tagReturn?.type
                } ?: TyMultipleResults(listOf(Ty.UNKNOWN), true)

                val guessedReturnTy = context.withMultipleResults {
                    o.exprList?.guessType(context)
                } ?: Ty.VOID

                val guessedReturnTyLists = if (guessedReturnTy is TyUnion && guessedReturnTy.getChildTypes().any { it is TyMultipleResults }) {
                    guessedReturnTy.getChildTypes().map { toList(it) }
                } else {
                    listOf(toList(guessedReturnTy))
                }

                val statementDocTagType = o.comment?.let { PsiTreeUtil.getChildrenOfTypeAsList(it, LuaDocTagTypeImpl::class.java).firstOrNull() }
                val statementDocTy = statementDocTagType?.getType()

                val processCandidate = fun(guessedReturnTyList: List<ITy>, candidateReturnTy: ITy): Collection<Problem> {
                    val problems = mutableListOf<Problem>()

                    val abstractTys = toList(statementDocTy ?: candidateReturnTy)
                    val variadicAbstractType = if (candidateReturnTy is TyMultipleResults && candidateReturnTy.variadic) {
                        candidateReturnTy.list.last()
                    } else null

                    for (i in 0 until guessedReturnTyList.size) {
                        val element = o.exprList?.getExpressionAt(i) ?: o
                        val targetType = abstractTys.getOrNull(i) ?: variadicAbstractType ?: Ty.VOID
                        val varianceFlags = if (element is LuaTableExpr) TyVarianceFlags.WIDEN_TABLES else 0

                        ProblemUtil.contravariantOf(targetType, guessedReturnTyList[i], context, varianceFlags, null, element) { problem ->
                            val targetMessage = problem.message

                            if (guessedReturnTyList.size > 1) {
                                problem.message = "Result ${i + 1}, ${targetMessage.decapitalize()}"
                            }

                            problems.add(problem)

                            if (problem.targetElement != null && problem.targetElement != problem.sourceElement) {
                                problems.add(Problem(null, problem.targetElement, targetMessage, problem.highlightType))
                            }
                        }
                    }

                    val abstractReturnCount = if (variadicAbstractType != null) {
                        abstractTys.size - 1
                    } else abstractTys.size

                    val concreteReturnCount = if (guessedReturnTy is TyMultipleResults && guessedReturnTy.variadic) {
                        guessedReturnTyList.size - 1
                    } else guessedReturnTyList.size

                    if (concreteReturnCount < abstractReturnCount) {
                        problems.add(
                            Problem(
                                null,
                                o.lastChild,
                                "Incorrect number of values. Expected %s but found %s.".format(abstractReturnCount, concreteReturnCount)
                            )
                        )
                    }

                    if (statementDocTy != null) {
                        val expectedReturnTys = toList(candidateReturnTy)
                        val expectedVariadicReturnTy = if (candidateReturnTy is TyMultipleResults && candidateReturnTy.variadic) {
                            candidateReturnTy.list.last()
                        } else null

                        for (i in 0 until abstractTys.size) {
                            val targetType = expectedReturnTys.getOrNull(i) ?: expectedVariadicReturnTy ?: Ty.VOID

                            if (!targetType.contravariantOf(abstractTys[i], context, 0)) {
                                val element = statementDocTagType.typeList?.tyList?.let { it.getOrNull(i) ?: it.last() } ?: statementDocTagType
                                val message = "Type mismatch. Required: '%s' Found: '%s'".format(targetType.displayName, abstractTys[i].displayName)
                                problems.add(Problem(null, element, message))
                            }
                        }

                        val candidateReturnCount = if (expectedVariadicReturnTy != null) {
                            expectedReturnTys.size - 1
                        } else expectedReturnTys.size

                        if (abstractReturnCount < candidateReturnCount) {
                            val element = statementDocTagType.typeList ?: statementDocTagType
                            val message = "Incorrect number of values. Expected %s but found %s.".format(candidateReturnCount, abstractReturnCount)
                            problems.add(Problem(null, element, message))
                        }
                    }

                    return problems
                }
                val multipleCandidates = functionReturnDocTy is TyUnion && functionReturnDocTy.getChildTypes().any { it is TyMultipleResults }

                for (guessedReturnTyList in guessedReturnTyLists) {
                    if (multipleCandidates) {
                        val candidateProblems = mutableMapOf<String, Collection<Problem>>()
                        var matchFound = false

                        TyUnion.each(functionReturnDocTy) {
                            val problems = processCandidate(guessedReturnTyList, it)

                            if (problems.size == 0) {
                                matchFound = true
                                return@each
                            }

                            candidateProblems.put(it.displayName, problems)
                        }

                        if (matchFound) {
                            continue
                        }

                        candidateProblems.forEach { candidate, problems ->
                            problems.forEach {
                                val message = "${it.message} for candidate return type (${candidate})"
                                myHolder.registerProblem(it.sourceElement, message, it.highlightType ?: ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                            }
                        }
                    } else {
                        processCandidate(guessedReturnTyList, functionReturnDocTy).forEach {
                            myHolder.registerProblem(it.sourceElement, it.message, it.highlightType ?: ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                        }
                    }
                }
            }

            private fun toList(type: ITy): List<ITy> {
                return when (type) {
                    Ty.VOID -> emptyList()
                    is TyMultipleResults -> type.list
                    else -> listOf(type)
                }
            }

            private fun guessSuperReturnTypes(function: LuaClassMethodDefStat, context: SearchContext): ITy? {
                val comment = function.comment
                if (comment != null) {
                    if (comment.isOverride()) {
                        // Find super type
                        val superClass = function.guessClassType(context)
                        val superMember = superClass?.findSuperMember(function.name ?: "", context)
                        if (superMember is LuaClassMethodDefStat) {
                            return superMember.guessReturnType(context)
                        }
                    } else {
                        return comment.tagReturn?.type
                    }
                }
                return null
            }

            override fun visitFuncBody(o: LuaFuncBody) {
                super.visitFuncBody(o)

                // If some return type is defined, we require at least one return type
                val returnStat = PsiTreeUtil.findChildOfType(o, LuaReturnStat::class.java)

                if (returnStat == null) {
                    // Find function definition
                    val context = SearchContext.get(o.project)
                    val bodyOwner = PsiTreeUtil.getParentOfType(o, LuaFuncBodyOwner::class.java)

                    val type = if (bodyOwner is LuaClassMethodDefStat) {
                        guessSuperReturnTypes(bodyOwner, context)
                    } else {
                        val returnDef = (bodyOwner as? LuaCommentOwner)?.comment?.tagReturn
                        returnDef?.type
                    }

                    if (type != null && type != Ty.VOID && o.textLength != 0) {
                        myHolder.registerProblem(o, "Return type '%s' specified but no return values found.".format(type.displayName))
                    }
                }
            }
        }
    }
}
