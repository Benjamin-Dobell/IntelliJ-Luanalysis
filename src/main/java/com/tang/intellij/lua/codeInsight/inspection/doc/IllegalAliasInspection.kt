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
import com.tang.intellij.lua.comment.psi.LuaDocTagAlias
import com.tang.intellij.lua.comment.psi.LuaDocVisitor
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.ITy
import com.tang.intellij.lua.ty.ITyGeneric
import com.tang.intellij.lua.ty.ITyResolvable
import com.tang.intellij.lua.ty.TyUnion

class IllegalAliasInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : LuaDocVisitor() {
            override fun visitTagAlias(o: LuaDocTagAlias) {
                val alias = o.type
                val context = SearchContext.get(o.project)
                val resolved = alias.resolve(context)

                val visitedTys = mutableSetOf<ITy>()
                val pendingTys =  if (resolved is TyUnion) {
                    visitedTys.add(resolved)
                    mutableListOf<ITy>().apply { addAll(resolved.getChildTypes()) }
                } else {
                    mutableListOf(resolved)
                }

                while (pendingTys.isNotEmpty()) {
                    val pendingTy = pendingTys.removeAt(pendingTys.size - 1)

                    if (visitedTys.add(pendingTy)) {
                        if (pendingTy == alias || (pendingTy as? ITyGeneric)?.base == alias) {
                            holder.registerProblem(o, "Alias '${alias.name}' circularly references itself.", ProblemHighlightType.ERROR)
                            return
                        }

                        val resolvedMemberTy = (pendingTy as? ITyResolvable)?.resolve(context) ?: pendingTy

                        if (resolvedMemberTy != pendingTy) {
                            if (resolvedMemberTy is TyUnion) {
                                pendingTys.addAll(resolvedMemberTy.getChildTypes())
                            } else {
                                pendingTys.add(resolvedMemberTy)
                            }
                        }
                    }
                }
            }
        }
    }
}
