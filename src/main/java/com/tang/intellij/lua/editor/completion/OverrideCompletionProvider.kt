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

package com.tang.intellij.lua.editor.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.lang.LuaIcons
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassMemberIndex
import com.tang.intellij.lua.ty.*

/**
 * override supper
 * Created by tangzx on 2016/12/25.
 */
class OverrideCompletionProvider : LuaCompletionProvider() {
    override fun addCompletions(session: CompletionSession) {
        val completionParameters = session.parameters
        val completionResultSet = session.resultSet
        val id = completionParameters.position
        val methodDef = PsiTreeUtil.getParentOfType(id, LuaClassMethodDefStat::class.java)
        if (methodDef != null) {
            val context = SearchContext.get(methodDef.project)
            val classType = methodDef.guessParentClass(context)
            if (classType != null) {
                val memberNameSet = mutableSetOf<String>()
                classType.processMembers(context, false) { _, m ->
                    m.name?.let { memberNameSet.add(it) }
                    true
                }
                Ty.processSuperClasses(context, classType) { sup ->
                    val clazz = (if (sup is ITyGeneric) sup.base else sup) as? ITyClass
                    if (clazz != null) {
                        addOverrideMethod(completionParameters, completionResultSet, memberNameSet, clazz)
                    }
                    true
                }
            }
        }
    }

    private fun addOverrideMethod(completionParameters: CompletionParameters, completionResultSet: CompletionResultSet, memberNameSet: MutableSet<String>, sup: ITyClass) {
        val project = completionParameters.originalFile.project
        val context = SearchContext.get(project)
        val clazzName = sup.className
        LuaClassMemberIndex.processAll(context, TyLazyClass(clazzName)) { _, member ->
            member.name?.let { memberName ->
                if (!memberNameSet.contains(memberName)) {
                    if (member is LuaTypeMethod<*>) {
                        val elementBuilder = LookupElementBuilder.create(memberName)
                            .withIcon(LuaIcons.CLASS_METHOD_OVERRIDING)
                            .withInsertHandler(OverrideInsertHandler(member.params, member.varargType != null))
                            .withTailText(member.paramSignature)

                        completionResultSet.addElement(elementBuilder)
                        memberNameSet.add(memberName)
                    } else {
                        val memberTy = member.guessType(context)

                        if (memberTy is ITyFunction) {
                            val mainSignature = memberTy.mainSignature
                            val params = mainSignature.params ?: arrayOf()

                            val elementBuilder = LookupElementBuilder.create(memberName)
                                .withIcon(LuaIcons.CLASS_METHOD_OVERRIDING)
                                .withInsertHandler(OverrideInsertHandler(params, mainSignature.hasVarargs()))
                                .withTailText(getParamSignature(params))

                            completionResultSet.addElement(elementBuilder)
                            memberNameSet.add(memberName)
                        }
                    }
                }
            }
            true
        }
    }

    internal class OverrideInsertHandler(override val params: Array<out LuaParamInfo>, override val isVarargs: Boolean) : ArgsInsertHandler() {

        override val autoInsertParameters = true

        override fun createTemplate(manager: TemplateManager, paramDefList: Array<out LuaParamInfo>): Template {
            val template = super.createTemplate(manager, paramDefList)
            template.addEndVariable()
            template.addTextSegment("\nend")
            return template
        }
    }
}
