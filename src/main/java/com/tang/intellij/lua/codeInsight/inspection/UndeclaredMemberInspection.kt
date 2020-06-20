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

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.tang.intellij.lua.psi.LuaIndexExpr
import com.tang.intellij.lua.psi.LuaVisitor
import com.tang.intellij.lua.psi.prefixExpr
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.TyAliasSubstitutor
import com.tang.intellij.lua.ty.TySnippet
import com.tang.intellij.lua.ty.TyUnion

class UndeclaredMemberInspection : StrictInspection() {
    override fun buildVisitor(myHolder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
            object : LuaVisitor() {
                override fun visitIndexExpr(o: LuaIndexExpr) {
                    val context = SearchContext.get(o)
                    val prefix = TyAliasSubstitutor.substitute(o.prefixExpr.guessType(context), context)
                    val memberName = o.name

                    TyUnion.each(prefix) { prefixTy ->
                        if (memberName != null) {
                            if (prefixTy.guessMemberType(memberName, context) == null) {
                                myHolder.registerProblem(o, "No such member '%s' found on type '%s'".format(memberName, prefixTy))
                            }
                        } else {
                            o.idExpr?.guessType(context)?.let { indexTy ->
                                TyUnion.each(indexTy) {
                                    if (it !is TySnippet && prefixTy.guessIndexerType(it, context) == null) {
                                        myHolder.registerProblem(o, "No such indexer '[%s]' found on type '%s'".format(it.displayName, prefixTy))
                                    }
                                }

                            }
                        }
                    }

                    super.visitIndexExpr(o)
                }
            }
}
