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

import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.tang.intellij.lua.lang.LuaIcons
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*

enum class MemberCompletionMode {
    Dot,    // self.xxx
    Colon,  // self:xxx()
    All     // self.xxx && self:xxx()
}

/**

 * Created by tangzx on 2016/12/25.
 */
open class ClassMemberCompletionProvider : LuaCompletionProvider() {
    protected abstract class HandlerProcessor {
        open fun processLookupString(lookupString: String, member: TypeMember, memberTy: ITy?): String = lookupString
        abstract fun process(element: LuaLookupElement, member: TypeMember, memberTy: ITy?): LookupElement
    }

    override fun addCompletions(session: CompletionSession) {
        val completionParameters = session.parameters
        val completionResultSet = session.resultSet

        val psi = completionParameters.position
        val indexExpr = psi.parent

        if (indexExpr is LuaIndexExpr) {
            val isColon = indexExpr.colon != null
            val project = indexExpr.project
            val context = SearchContext.get(project)
            val contextTy = LuaPsiTreeUtil.findContextClass(indexExpr, context)
            val prefixType = indexExpr.guessParentType(context)
            if (!Ty.isInvalid(prefixType)) {
                complete(context, isColon, contextTy, prefixType, completionResultSet, completionResultSet.prefixMatcher, null)
            }
            //smart
            val nameExpr = indexExpr.prefixExpression
            if (nameExpr is LuaNameExpr) {
                val colon = if (isColon) ":" else "."
                val prefixName = nameExpr.text
                val postfixName = indexExpr.name?.let { it.substring(0, it.indexOf(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED)) }

                val matcher = completionResultSet.prefixMatcher.cloneWithPrefix(prefixName)
                LuaDeclarationTree.get(indexExpr.containingFile).walkUpLocal(indexExpr) { d ->
                    val it = d.firstDeclaration.psi
                    val txt = it.name
                    if (it is LuaPsiTypeGuessable && txt != null && prefixName != txt && matcher.prefixMatches(txt)) {
                        val type = it.guessType(context)
                        if (type != null) {
                            val prefixMatcher = completionResultSet.prefixMatcher
                            val resultSet = completionResultSet.withPrefixMatcher("$prefixName*$postfixName")
                            complete(context, isColon, contextTy, type, resultSet, prefixMatcher, object : HandlerProcessor() {
                                override fun process(element: LuaLookupElement, member: TypeMember, memberTy: ITy?): LookupElement {
                                    element.itemText = txt + colon + element.itemText
                                    element.lookupString = txt + colon + element.lookupString
                                    return PrioritizedLookupElement.withPriority(element, -2.0)
                                }
                            })
                        }
                    }
                    true
                }
            }
        }
    }

    private fun complete(context: SearchContext,
                         isColon: Boolean,
                         contextTy: ITy,
                         prefixTy: ITy,
                         completionResultSet: CompletionResultSet,
                         prefixMatcher: PrefixMatcher,
                         handlerProcessor: HandlerProcessor?) {
        val mode = if (isColon) MemberCompletionMode.Colon else MemberCompletionMode.Dot
        val resolvedPrefixTy = Ty.resolve(prefixTy, context)

        if (resolvedPrefixTy is TyUnion) {
            addUnion(context, contextTy, resolvedPrefixTy, prefixTy, mode, completionResultSet, prefixMatcher, handlerProcessor)
        } else {
            addClass(context, contextTy, prefixTy, mode, completionResultSet, prefixMatcher, handlerProcessor)
        }
    }

    protected fun addUnion(context: SearchContext,
                           contextTy: ITy,
                           unionTy: TyUnion,
                           prefixTy: ITy,
                           completionMode: MemberCompletionMode,
                           completionResultSet: CompletionResultSet,
                           prefixMatcher: PrefixMatcher,
                           handlerProcessor: HandlerProcessor?) {
        val globalChildTys = unionTy.getChildTypes().filter { it.isGlobal }
        val nonGlobalChildTys = unionTy.getChildTypes().filter { !it.isGlobal }

        globalChildTys.forEach {
            addClass(context, contextTy, it, completionMode, completionResultSet, prefixMatcher, handlerProcessor)
        }

        if (nonGlobalChildTys.isEmpty()) {
            return
        }

        val firstChildTy = nonGlobalChildTys.first()
        val subsequentChildTys = nonGlobalChildTys.drop(1)
        val memberSubstitutor = firstChildTy.getMemberSubstitutor(context)

        firstChildTy.processMembers(context) { curType, member ->
            val curClass = (if (curType is ITyGeneric) curType.base else curType) as? ITyClass

            if (curClass != null) {
                member.name?.let { memberName ->
                    if (prefixMatcher.prefixMatches(memberName) && curClass.isVisibleInScope(context.project, contextTy, member.visibility)) {
                        var memberTy = member.guessType(context) ?: Primitives.UNKNOWN

                        subsequentChildTys.forEach { childTy ->
                            if (!childTy.isGlobal) {
                                val ty = childTy.guessMemberType(memberName, context)

                                if (ty == null) {
                                    return@processMembers true
                                }

                                memberTy = memberTy.union(ty, context)
                            }
                        }

                        addMember(
                            context,
                            completionResultSet,
                            member,
                            memberSubstitutor,
                            prefixTy,
                            memberTy,
                            completionMode,
                            handlerProcessor
                        )
                    }
                }
            }

            true
        }
    }

    protected fun addClass(context: SearchContext,
                           contextTy: ITy,
                           cls: ITy,
                           completionMode: MemberCompletionMode,
                           completionResultSet: CompletionResultSet,
                           prefixMatcher: PrefixMatcher,
                           handlerProcessor: HandlerProcessor?) {
        cls.processMembers(context) { memberClass, member ->
            val curClass = (if (memberClass is ITyGeneric) memberClass.base else memberClass) as? ITyClass
            if (curClass != null) {
                member.name?.let {
                    if (prefixMatcher.prefixMatches(it) && curClass.isVisibleInScope(context.project, contextTy, member.visibility)) {
                        addMember(context,
                            completionResultSet,
                            member,
                            memberClass.getMemberSubstitutor(context),
                            memberClass,
                            member.guessType(context) ?: Primitives.UNKNOWN,
                            completionMode,
                            handlerProcessor)
                    }
                }
            }
            true
        }
    }

    protected fun addMember(context: SearchContext,
                            completionResultSet: CompletionResultSet,
                            member: TypeMember,
                            memberSubstitutor: ITySubstitutor?,
                            thisType: ITy,
                            memberTy: ITy,
                            completionMode: MemberCompletionMode,
                            handlerProcessor: HandlerProcessor?) {
        val bold = thisType == memberTy
        val className = thisType.displayName

        if (memberTy is ITyFunction) {
            val methodType = if (memberSubstitutor != null) {
                memberSubstitutor.substitute(memberTy)
            } else {
                memberTy
            }

            val fn = memberTy.substitute(TySelfSubstitutor(context, null, methodType))
            addFunction(completionResultSet, bold, completionMode != MemberCompletionMode.Dot, className, member, fn, thisType, thisType, handlerProcessor)
        } else if (member is LuaTypeField && completionMode != MemberCompletionMode.Colon) {
            val fieldType = if (memberSubstitutor != null) {
                memberSubstitutor.substitute(memberTy)
            } else {
                memberTy
            }

            addField(completionResultSet, bold, className, member, fieldType, handlerProcessor)
        }
    }

    protected fun addField(completionResultSet: CompletionResultSet,
                           bold: Boolean,
                           clazzName: String,
                           field: LuaTypeField,
                           ty: ITy?,
                           handlerProcessor: HandlerProcessor?) {
        val name = field.name
        if (name != null) {
            val element = LookupElementFactory.createFieldLookupElement(clazzName, name, field, ty, bold)
            val ele = handlerProcessor?.process(element, field, null) ?: element
            completionResultSet.addElement(ele)
        }
    }

    private fun addFunction(completionResultSet: CompletionResultSet,
                            bold: Boolean,
                            isColonStyle: Boolean,
                            clazzName: String,
                            classMember: TypeMember,
                            ty: ITy,
                            thisType: ITy,
                            callType: ITy,
                            handlerProcessor: HandlerProcessor?) {
        val name = classMember.name
        if (name != null) {
            val context = SearchContext.get(classMember.psi.project)

            ty.processSignatures(context) {
                if (isColonStyle) {
                    val firstParamTy = it.getFirstParam(thisType, isColonStyle)?.ty
                    if (firstParamTy == null || !firstParamTy.contravariantOf(callType, context, TyVarianceFlags.STRICT_UNKNOWN)) {
                        return@processSignatures true
                    }
                }

                val lookupString = handlerProcessor?.processLookupString(name, classMember, ty) ?: name

                val element = LookupElementFactory.createMethodLookupElement(clazzName,
                        lookupString,
                        classMember,
                        it,
                        bold,
                        isColonStyle,
                        ty,
                        LuaIcons.CLASS_METHOD)
                val ele = handlerProcessor?.process(element, classMember, ty) ?: element
                completionResultSet.addElement(ele)
                true
            }
        }
    }
}
