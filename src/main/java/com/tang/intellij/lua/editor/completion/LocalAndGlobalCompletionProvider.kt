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

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.tree.TokenSet
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.lang.LuaIcons
import com.tang.intellij.lua.lang.LuaParserDefinition
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*

/**
 * suggest local/global vars and functions
 * Created by TangZX on 2017/4/11.
 */
class LocalAndGlobalCompletionProvider(private val mask: Int) : ClassMemberCompletionProvider() {

    private fun has(flag: Int): Boolean {
        return mask and flag == flag
    }

    private fun addCompletion(name:String, session: CompletionSession, psi: LuaPsiElement) {
        val ctx = SearchContext.get(psi.project)
        val addTy = {ty: ITyFunction ->
            val icon = if (ty.isGlobal)
                LuaIcons.GLOBAL_FUNCTION
            else
                LuaIcons.LOCAL_FUNCTION

            ty.processSignatures(ctx) {
                val le = LookupElementFactory.createFunctionLookupElement(name, psi, it, false, ty, icon)
                session.resultSet.addElement(le)
                true
            }
        }
        when (psi) {
            is LuaFuncBodyOwner<*> -> {
                psi.guessType(ctx)?.let {
                    TyUnion.each(it) {
                        if (it is ITyFunction) {
                            addTy(it)
                        }
                    }
                }
            }
            is LuaPsiTypeGuessable -> {
                val type = psi.guessType(ctx)

                if (type == null) {
                    return
                }

                var isFn = false
                TyUnion.each(type) {
                    if (it is ITyFunction) {
                        isFn = true
                        addTy(it)
                    }
                }
                if (!isFn) {
                    var icon = LuaIcons.LOCAL_VAR
                    if (psi is LuaParamDef)
                        icon = LuaIcons.PARAMETER

                    val elementBuilder = LookupElementFactory.createGuessableLookupElement(name, psi, type, icon)
                    session.resultSet.addElement(elementBuilder)
                }
            }
        }
    }

    override fun addCompletions(session: CompletionSession) {
        val completionParameters = session.parameters
        val completionResultSet = session.resultSet
        val cur = completionParameters.position
        val nameExpr = cur.parent
        val context = SearchContext.get(nameExpr.project)

        //module members
        if (nameExpr is LuaNameExpr) {
            val moduleName = nameExpr.getModuleName(context)
            if (moduleName != null) {
                val ty = TyLazyClass(moduleName, nameExpr)
                val contextTy = LuaPsiTreeUtil.findContextClass(context, nameExpr)
                addClass(context, contextTy, ty, MemberCompletionMode.Dot, completionResultSet, completionResultSet.prefixMatcher, null)
            }
        }

        //local names
        val localNamesSet = mutableSetOf<String>()

        //local
        if (has(LOCAL_FUN) || has(LOCAL_VAR)) {
            LuaDeclarationTree.get(cur.containingFile).walkUpLocal(cur) {
                val localDef = it.psi
                val name = it.name
                if (localDef is LuaPsiElement &&
                        completionResultSet.prefixMatcher.prefixMatches(name) &&
                        localNamesSet.add(name)) {
                    session.addWord(name)
                    addCompletion(name, session, localDef)
                }
                true
            }
        }

        //global
        if (has(GLOBAL_FUN) || has(GLOBAL_VAR)) {
            addClass(context, TyClass.G, TyClass.G, MemberCompletionMode.Dot, completionResultSet, completionResultSet.prefixMatcher, null)
        }
        //key words
        if (has(KEY_WORDS)) {
            for (keyWordToken in KEYWORD_TOKENS.types) {
                session.addWord(keyWordToken.toString())

                completionResultSet.addElement(LookupElementFactory.createKeyWordLookupElement(keyWordToken))
            }
            for (primitiveToken in LuaParserDefinition.PRIMITIVE_TYPE_SET.types) {
                session.addWord(primitiveToken.toString())
                completionResultSet.addElement(LookupElementBuilder.create(primitiveToken))
            }
            completionResultSet.addElement(LookupElementBuilder.create(Constants.WORD_SELF))
        }
    }

    companion object {

        private const val LOCAL_VAR = 1
        private const val LOCAL_FUN = 2
        private const val GLOBAL_VAR = 4
        private const val GLOBAL_FUN = 8
        private const val KEY_WORDS = 16

        @JvmStatic val ALL = LOCAL_VAR or LOCAL_FUN or GLOBAL_VAR or GLOBAL_FUN or KEY_WORDS
        @JvmStatic val VARS = LOCAL_VAR or GLOBAL_VAR

        private val KEYWORD_TOKENS = TokenSet.create(
                LuaTypes.AND,
                LuaTypes.BREAK,
                LuaTypes.DO,
                LuaTypes.ELSE,
                //LuaTypes.ELSEIF,
                LuaTypes.END,
                //LuaTypes.FOR,
                LuaTypes.FUNCTION,
                //LuaTypes.IF,
                LuaTypes.IN,
                LuaTypes.LOCAL,
                LuaTypes.NOT,
                LuaTypes.OR,
                LuaTypes.REPEAT,
                LuaTypes.RETURN,
                LuaTypes.THEN,
                LuaTypes.UNTIL,
                LuaTypes.WHILE,

                //Lua5.3
                LuaTypes.GOTO,
                LuaTypes.DOUBLE_COLON
        )
    }
}
