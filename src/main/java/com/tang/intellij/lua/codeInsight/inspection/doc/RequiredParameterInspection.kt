/*
 * Copyright (c) 2023
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

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.tang.intellij.lua.psi.LuaElementFactory
import com.tang.intellij.lua.psi.LuaFuncBodyOwner
import com.tang.intellij.lua.psi.LuaVisitor
import com.tang.intellij.lua.stubs.LuaFuncBodyOwnerStub

class RequiredParameterInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : LuaVisitor() {

            override fun visitFuncBodyOwner(o: LuaFuncBodyOwner<out LuaFuncBodyOwnerStub<*>>) {
                super.visitFuncBodyOwner(o)

                var encounteredOptional = false
                val comment = o.comment

                if (comment == null) {
                    return
                }

                o.funcBody?.paramDefList?.forEach { param ->
                    val paramTag = comment.getParamDef(param.name)

                    if (paramTag == null) {
                        return@forEach
                    }

                    if (encounteredOptional) {
                        if (paramTag.optional == null) {
                            holder.registerProblem(param, "Required parameters cannot follow optional parameters", ProblemHighlightType.ERROR, object : LocalQuickFix {
                                override fun getFamilyName(): String {
                                    return "Make parameter optional"
                                }

                                override fun applyFix(program: Project, problem: ProblemDescriptor) {
                                    val name = paramTag.paramNameRef?.id?.text
                                    val type = paramTag.ty?.text

                                    if (paramTag.optional == null && name != null && type != null) {
                                        paramTag.replace(LuaElementFactory.createDocTagParam(paramTag.project, name, type, true, paramTag.commentString?.text))
                                    }
                                }

                            })
                        }
                    } else if (paramTag.optional != null) {
                        encounteredOptional = true
                    }
                }
            }
        }
    }
}
