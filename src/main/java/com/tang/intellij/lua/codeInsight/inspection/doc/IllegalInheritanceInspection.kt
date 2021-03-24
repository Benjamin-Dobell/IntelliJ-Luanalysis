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

package com.tang.intellij.lua.codeInsight.inspection.doc

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.tang.intellij.lua.comment.psi.LuaDocGenericDef
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.comment.psi.LuaDocVisitor
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*

class IllegalInheritanceInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : LuaDocVisitor() {
            override fun visitGenericDef(o: LuaDocGenericDef) {
                super.visitGenericDef(o)
            }

            override fun visitTagClass(o: LuaDocTagClass) {
                o.superClass?.let { superClass ->
                    val superTy = superClass.getType()

                    val context = SearchContext.get(o.project)
                    val superTyBase = if (superTy is ITyGeneric) superTy.base else superTy
                    val resolvedSuperTyBase = Ty.resolve(superTyBase, context)

                    if (resolvedSuperTyBase is ITyPrimitive || resolvedSuperTyBase is TyPrimitiveLiteral) {
                        holder.registerProblem(o, "Illegal inheritance from primitive type", ProblemHighlightType.ERROR)
                        return
                    }

                    if (resolvedSuperTyBase is ITyArray) {
                        holder.registerProblem(o, "Illegal inheritance from array", ProblemHighlightType.ERROR)
                        return
                    }

                    val className = o.id.text
                    val parentClassName = resolvedSuperTyBase.displayName

                    if (parentClassName == className) {
                        holder.registerProblem(o, "Illegal self inheritance", ProblemHighlightType.ERROR)
                        return
                    }

                    var previousAncestorName = parentClassName

                    Ty.processSuperClasses(resolvedSuperTyBase, context) {
                        val ancestor = if (it is ITyGeneric) it.base else it
                        val resolvedAncestor = Ty.resolve(ancestor, context)

                        val ancestorName = resolvedAncestor.displayName

                        if (ancestorName == className) {
                            holder.registerProblem(o, "Illegal cyclical inheritance from ${previousAncestorName}", ProblemHighlightType.ERROR)
                            return@processSuperClasses false
                        }

                        previousAncestorName = ancestorName

                        true
                    }
                }
            }
        }
    }
}
