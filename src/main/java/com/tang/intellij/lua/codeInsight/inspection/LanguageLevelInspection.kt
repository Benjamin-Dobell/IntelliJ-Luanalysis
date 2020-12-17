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

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.tang.intellij.lua.lang.LuaFileType
import com.tang.intellij.lua.lang.LuaLanguageLevel
import com.tang.intellij.lua.lang.LuaParserDefinition
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.project.StdLibraryProvider
import com.tang.intellij.lua.psi.*

class LanguageLevelInspection : LocalInspectionTool() {

    private fun registerLuaLevelProblem(o: PsiElement, requiredLevel: LuaLanguageLevel, desc: String, holder: ProblemsHolder) {
        holder.registerProblem(o, desc, object : LocalQuickFix {
            override fun getFamilyName() = "Upgrade language level to ${requiredLevel}"

            override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                LuaSettings.instance.languageLevel = requiredLevel
                StdLibraryProvider.reload()
            }
        })
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        if (session.file.name.matches(LuaFileType.DEFINITION_FILE_REGEX)) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : LuaVisitor() {
            override fun visitBinaryOp(o: LuaBinaryOp) {
                if (o.languageLevel < LuaLanguageLevel.LUA53 && LuaParserDefinition.LUA53_BIN_OP_SET.contains(o.node.firstChildNode.elementType)) {
                    val desc = "The binary operator '${o.text}' only available in Lua 5.3 and above"
                    registerLuaLevelProblem(o, LuaLanguageLevel.LUA53, desc, holder)
                }
            }

            override fun visitUnaryOp(o: LuaUnaryOp) {
                if (o.languageLevel < LuaLanguageLevel.LUA53 && LuaParserDefinition.LUA53_UNARY_OP_SET.contains(o.node.firstChildNode.elementType)) {
                    val desc = "The unary operator '${o.text}' only available in Lua 5.3 and above"
                    registerLuaLevelProblem(o, LuaLanguageLevel.LUA52, desc, holder)
                }
            }

            override fun visitAttribute(o: LuaAttribute) {
                super.visitAttribute(o)
                if (o.languageLevel < LuaLanguageLevel.LUA54) {
                    registerLuaLevelProblem(o,
                            LuaLanguageLevel.LUA54,
                            "Attributes are only available in Lua 5.4 and above",
                            holder)
                }
            }
        }
    }
}
