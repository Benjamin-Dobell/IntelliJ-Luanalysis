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
                val expectedReturnType = if (bodyOwner is LuaClassMethodDefStat) {
                    guessSuperReturnTypes(bodyOwner, context)
                } else {
                    bodyOwner.tagReturn?.type
                } ?: TyMultipleResults(listOf(Ty.UNKNOWN), true)

                val concreteType = context.withMultipleResults {
                    o.exprList?.guessType(context)?.let {
                        TyMultipleResults.flatten(context, it)
                    }
                } ?: Ty.VOID

                val concreteTypes = toList(concreteType)

                val documentedReturnTypeTag = o.comment?.let { PsiTreeUtil.getChildrenOfTypeAsList(it, LuaDocTagTypeImpl::class.java).firstOrNull() }
                val documentedType = documentedReturnTypeTag?.getType()

                val abstractTypes = toList(documentedType ?: expectedReturnType)
                val variadicAbstractType = if (expectedReturnType is TyMultipleResults && expectedReturnType.variadic) {
                    expectedReturnType.list.last()
                } else null

                for (i in 0 until concreteTypes.size) {
                    val element = o.exprList?.getExpressionAt(i) ?: o
                    val targetType = abstractTypes.getOrNull(i) ?: variadicAbstractType ?: Ty.VOID
                    val varianceFlags = if (element is LuaTableExpr) TyVarianceFlags.WIDEN_TABLES else 0

                    ProblemUtil.contravariantOf(targetType, concreteTypes[i], context, varianceFlags, null, element) { problem ->
                        val sourceElement = problem.sourceElement
                        val targetElement = problem.targetElement
                        val sourceMessage = if (concreteTypes.size > 1) "Result ${i + 1}, ${problem.message.decapitalize()}" else problem.message
                        val highlightType = problem.highlightType ?: ProblemHighlightType.GENERIC_ERROR_OR_WARNING

                        myHolder.registerProblem(sourceElement, sourceMessage, highlightType)

                        if (targetElement != null && targetElement != sourceElement) {
                            myHolder.registerProblem(targetElement, problem.message, highlightType)
                        }
                    }
                }

                val abstractReturnCount = if (variadicAbstractType != null) {
                    abstractTypes.size - 1
                } else abstractTypes.size

                val concreteReturnCount = if (concreteType is TyMultipleResults && concreteType.variadic) {
                    concreteTypes.size - 1
                } else concreteTypes.size

                if (concreteReturnCount < abstractReturnCount) {
                    myHolder.registerProblem(o.lastChild, "Incorrect number of values. Expected %s but found %s.".format(abstractReturnCount, concreteReturnCount))
                }

                if (documentedType != null) {
                    val expectedReturnTypes = toList(expectedReturnType)
                    val variadicExpectedReturnType = if (expectedReturnType is TyMultipleResults && expectedReturnType.variadic) {
                        expectedReturnType.list.last()
                    } else null

                    for (i in 0 until abstractTypes.size) {
                        val targetType = expectedReturnTypes.getOrNull(i) ?: variadicExpectedReturnType ?: Ty.VOID

                        if (!targetType.contravariantOf(abstractTypes[i], context, 0)) {
                            val element = documentedReturnTypeTag.typeList?.tyList?.let { it.getOrNull(i) ?: it.last() } ?: documentedReturnTypeTag
                            val message = "Type mismatch. Required: '%s' Found: '%s'".format(targetType.displayName, abstractTypes[i].displayName)
                            myHolder.registerProblem(element, message)
                        }
                    }

                    val expectedReturnCount = if (variadicExpectedReturnType != null) {
                        expectedReturnTypes.size - 1
                    } else expectedReturnTypes.size

                    if (abstractReturnCount < expectedReturnCount) {
                        val element = documentedReturnTypeTag.typeList ?: documentedReturnTypeTag
                        val message = "Incorrect number of values. Expected %s but found %s.".format(expectedReturnCount, abstractReturnCount)
                        myHolder.registerProblem(element, message)
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
