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
import com.tang.intellij.lua.lang.LuaFileType
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.LuaCallExpr
import com.tang.intellij.lua.psi.LuaExpression
import com.tang.intellij.lua.psi.LuaIndexExpr
import com.tang.intellij.lua.psi.LuaVisitor
import com.tang.intellij.lua.search.ProjectSearchContext
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*

class MatchFunctionSignatureInspection : StrictInspection() {
    data class ConcreteTypeInfo(val param: LuaExpression<*>, val ty: ITy)

    override fun buildVisitor(myHolder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        if (session.file.name.matches(LuaFileType.DEFINITION_FILE_REGEX)) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : LuaVisitor() {
            override fun visitIndexExpr(o: LuaIndexExpr) {
                super.visitIndexExpr(o)
                val id = o.id
                if (id != null) {
                    if (o.parent is LuaCallExpr && o.colon != null) {
                        // Guess parent types
                        val context = SearchContext.get(o.project)
                        o.expressionList.forEach { expr ->
                            if (expr.guessType(context) == Primitives.NIL) {
                                // If parent type is nil add error
                                myHolder.registerProblem(expr, "Trying to index a nil type.")
                            }
                        }
                    }
                }
            }

            override fun visitCallExpr(o: LuaCallExpr) {
                super.visitCallExpr(o)

                val searchContext = ProjectSearchContext(o.project)
                val prefixExpr = o.expression
                var resolvedTy = prefixExpr.guessType(searchContext)?.let {
                    Ty.resolve(searchContext, it)
                } ?: Primitives.UNKNOWN

                if (resolvedTy is TyUnion && resolvedTy.size == 2 && resolvedTy.getChildTypes().last().isAnonymous) {
                    resolvedTy = resolvedTy.getChildTypes().first()
                }

                TyUnion.each(resolvedTy) {
                    if (it == Primitives.FUNCTION || (it.isUnknown && LuaSettings.instance.isUnknownCallable)) {
                        return@each
                    }

                    val matchResult = it.matchSignature(searchContext, o) { problem ->
                        myHolder.registerProblem(problem.sourceElement, problem.message, problem.highlightType ?: ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                    }

                    if (matchResult != null) {
                        return
                    }

                    if (prefixExpr is LuaIndexExpr) {
                        // Get parent type
                        val parentType = prefixExpr.guessParentType(searchContext)

                        if (parentType is TyClass) {
                            val memberName = prefixExpr.name
                            val idExpr = prefixExpr.idExpr

                            if (memberName != null) {
                                myHolder.registerProblem(o, "Unknown function '$memberName'.")
                            } else if (idExpr != null) {
                                myHolder.registerProblem(o, "Unknown function '[${it.displayName}]'.")
                            }
                        }
                    } else {
                        myHolder.registerProblem(o, "Unknown function '%s'.".format(prefixExpr.lastChild.text))
                    }
                }
            }
        }
    }
}
