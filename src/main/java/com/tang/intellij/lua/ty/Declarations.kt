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

package com.tang.intellij.lua.ty

import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.psi.LuaDocTagField
import com.tang.intellij.lua.ext.recursionGuard
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.PsiSearchContext
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.search.withRecursionGuard

fun infer(context: SearchContext, element: LuaPsiTypeGuessable?): ITy? {
    if (element == null)
        return null
    return SearchContext.infer(context, element)
}

internal fun inferInner(context: SearchContext, element: LuaPsiTypeGuessable): ITy? {
    return when (element) {
        is LuaFuncBodyOwner<*> -> element.infer(context)
        is LuaExpression<*> -> inferExpr(context, element)
        is LuaParamDef -> element.infer(context)
        is LuaLocalDef -> element.infer(context)
        is LuaDocTagField -> element.infer()
        is LuaTableField -> element.infer(context)
        is LuaPsiFile -> inferFile(context, element)
        else -> null
    }
}

fun inferReturnTy(context: SearchContext, owner: LuaFuncBodyOwner<*>): ITy? {
    return owner.stub?.guessReturnTy(context) ?: inferReturnTyInner(context, owner)
}

private fun inferReturnTyInner(context: SearchContext, owner: LuaFuncBodyOwner<*>): ITy? {
    val returnTag = owner.tagReturn

    if (returnTag != null) {
        return returnTag.type
    }

    //infer from return stat
    return withRecursionGuard("inferReturnTyInner", owner, context.isDumb) {
        var type: ITy? = Primitives.VOID

        owner.acceptChildren(object : LuaRecursiveVisitor() {
            override fun visitReturnStat(o: LuaReturnStat) {
                if (type == null) {
                    return
                }

                val c = PsiSearchContext(o)
                val returnTy = c.withConcreteGenericSupport(false) {
                    if (context.index != 0 || context.supportsMultipleResults) {
                        c.withIndex(context.index, context.supportsMultipleResults) {
                            guessReturnType(o, c)
                        }
                    } else {
                        guessReturnType(o, c)
                    }
                }

                if (returnTy == null) {
                    type = null
                } else {
                    TyUnion.each(returnTy) {
                        /**
                         * 注意，不能排除anonymous
                         * local function test()
                         *      local v = xxx()
                         *      v.yyy = zzz
                         *      return v
                         * end
                         *
                         * local r = test()
                         *
                         * type of r is an anonymous ty
                         */
                        type = type!!.union(context, it)
                    }
                }
            }

            override fun visitExprStat(o: LuaExprStat) {}
            override fun visitLabelStat(o: LuaLabelStat) {}
            override fun visitAssignStat(o: LuaAssignStat) {}
            override fun visitGotoStat(o: LuaGotoStat) {}
            override fun visitClassMethodDefStat(o: LuaClassMethodDefStat) {}
            override fun visitFuncDefStat(o: LuaFuncDefStat) {}
            override fun visitLocalDefStat(o: LuaLocalDefStat) {}
            override fun visitLocalFuncDefStat(o: LuaLocalFuncDefStat) {}
            override fun visitFuncBodyOwner(o: LuaFuncBodyOwner<*>) {}
        })
        type
    }
}

private fun LuaParamDef.infer(context: SearchContext): ITy {
    val ty = resolveParamType(context, this)

    if (ty == null) {
        val stub = this.stub
        val tyName = stub?.anonymousType ?: getAnonymousTypeName(this)
        return createSerializedClass(tyName, null, this.name, null, null, null, TyFlags.ANONYMOUS or TyFlags.UNKNOWN)
    }

    return ty
}

private fun LuaLocalDef.infer(context: SearchContext): ITy? {
    var type: ITy? = null
    val parent = this.parent
    if (parent is LuaTableField) {
        val expr = PsiTreeUtil.findChildOfType(parent, LuaExpression::class.java)
        if (expr != null) {
            // TODO: unknown vs. any - should be unknown
            type = infer(context, expr) ?: Primitives.UNKNOWN
        }
    } else {
        val docTy = this.docTy

        val localStat = PsiTreeUtil.getParentOfType(this, LuaLocalDefStat::class.java)

        if (localStat != null) {
            val index = localStat.getIndexFor(this)

            if (docTy != null) {
                return if (docTy is TyMultipleResults) {
                    docTy.list.getOrNull(index) ?: Primitives.NIL
                } else docTy
            }

            val exprList = localStat.exprList

            type = if (exprList != null) {
                // TODO: unknown vs. any - should be unknown
                val localContext = PsiSearchContext(localStat)
                localContext.withConcreteGenericSupport(context.supportsConcreteGenerics) {
                    localContext.withIndex(index, false) {
                        exprList.guessTypeAt(localContext)
                    }
                } ?: Primitives.UNKNOWN
            } else {
                val stub = this.stub
                val tyName = stub?.anonymousType ?: getAnonymousTypeName(this)
                createSerializedClass(tyName, null, this.name, null, null, null, TyFlags.ANONYMOUS or TyFlags.UNKNOWN)
            }

            if (type is TyPrimitiveLiteral) {
                type = type.primitiveType
            }
        } else if (docTy != null) {
            if (!context.supportsMultipleResults && docTy is TyMultipleResults) {
                return docTy.list.getOrNull(context.index) ?: Primitives.NIL
            } else return docTy
        }
    }
    return type
}

private fun LuaDocTagField.infer(): ITy? {
    val stub = stub
    if (stub != null)
        return stub.valueTy
    return valueType?.getType()
}

private fun LuaFuncBodyOwner<*>.infer(context: SearchContext): ITy {
    if (this is LuaFuncDefStat)
        return TyPsiFunction(false, this, TyFlags.GLOBAL)
    return if (this is LuaClassMethodDefStat) {
        TyPsiFunction(!this.isStatic, this, 0)
    } else TyPsiFunction(false, this, 0)
}

private fun LuaTableField.infer(context: SearchContext): ITy? {
    val stub = stub
    //from comment
    val docTy = if (stub != null) stub.docTy else comment?.docTy
    if (docTy != null)
        return docTy

    //guess from value
    return valueExpr?.let {
        context.withListEntry(name == null && idExpr == null && this == parent.children.last()) {
            infer(context, it)
        }
    }
}

fun LuaPsiFile.returnStatement(): LuaReturnStat? {
    if (this.getModuleName(SearchContext.get(this.project)) != null) {
        return null
    }

    return recursionGuard(this, {
        val stub = this.stub
        if (stub != null) {
            val statStub = stub.childrenStubs.lastOrNull { it.psi is LuaReturnStat }
            statStub?.psi as? LuaReturnStat
        } else {
            val lastChild = this.lastChild
            var stat: LuaReturnStat? = null
            LuaPsiTreeUtil.walkTopLevelInFile(lastChild, LuaReturnStat::class.java) {
                stat = it
                false
            }
            stat
        }
    })
}

private fun inferFile(context: SearchContext, file: LuaPsiFile): ITy {
    return recursionGuard(file, {
        val moduleName = file.getModuleName(context)
        if (moduleName != null)
            TyLazyClass(moduleName)
        else {
            file.returnStatement()?.let {
                context.withIndex(0) { guessReturnType(it, context) }
            } ?: Primitives.VOID
        }
    }) ?: Primitives.UNKNOWN
}

/**
 * 找参数的类型
 * @param paramDef param name
 * *
 * @param context SearchContext
 * *
 * @return LuaType
 */
private fun resolveParamType(context: SearchContext, paramDef: LuaParamDef): ITy? {
    val stub = paramDef.stub
    val paramName = stub?.name ?: paramDef.name
    var ty: ITy? = stub?.docTy

    val comment = PsiTreeUtil.getStubOrPsiParentOfType(paramDef, LuaCommentOwner::class.java)?.let { commentOwner ->
        commentOwner.comment
            ?: (commentOwner.parent?.parent as? LuaDeclaration)?.comment // Doc comment may appear on declarations
    }


    if (comment != null) {
        comment.getParamDef(paramName)?.let { paramTag ->
            val contextElement = context.element

            if (paramTag.optional != null
                && contextElement != null
                && PsiTreeUtil.isAncestor(comment.owner!!, contextElement, true)
            ) {
                // Within the scope of a function, optional parameters may be nil
                ty = paramTag.type.union(context, Primitives.NIL)
            } else {
                ty = paramTag.type
            }
        }
    }

    if (ty != null) {
        return ty
    }

    val paramOwner = PsiTreeUtil.getStubOrPsiParentOfType(paramDef, LuaParametersOwner::class.java)

    when (paramOwner) {
        is LuaClassMethodDefStat -> {
            val effectiveMember = paramOwner.guessParentClass(context)?.let { parentClass ->
                paramOwner.name?.let { name -> parentClass.findEffectiveMember(context, name) }
            }

            if (effectiveMember != null && effectiveMember != paramOwner) {
                val param = (effectiveMember.guessType(context) as? ITyFunction)?.let {
                    it.mainSignature.params?.getOrNull(paramOwner.getIndexFor(paramDef))
                }
                param?.ty?.let { paramTy ->
                    val contextElement = context.element

                    return if (param.optional
                        && contextElement != null
                        && PsiTreeUtil.isAncestor(paramOwner, contextElement, true)
                    ) {
                        // Within the scope of a function, optional parameters may be nil
                        paramTy.union(context, Primitives.NIL)
                    } else {
                        paramTy
                    }
                }
            }
        }

        is LuaFuncDefStat -> {
            if (paramName == Constants.WORD_SELF) {
                // module fun
                // function method(self) end

                val moduleName = paramDef.getModuleName(context)
                if (moduleName != null) {
                    ty = TyLazyClass(moduleName)
                }
            }
        }

        is LuaForBStat -> {
            // for (iterator support)

            val exprList = paramOwner.exprList
            val paramIndex = paramOwner.getIndexFor(paramDef)

            val iterator: ITyFunction?

            val callExpr = exprList?.expressionList?.firstOrNull() as? LuaCallExpr

            if (callExpr != null) {
                iterator = context.withMultipleResults {
                    callExpr.guessType(context)
                }?.let {
                    TyMultipleResults.getResult(context, it, 0) as? ITyFunction
                }
            } else {
                iterator = (exprList?.expressionList?.firstOrNull() as? LuaPsiTypeGuessable)?.guessType(context) as? ITyFunction
            }

            if (iterator != null) {
                val returnTy = iterator.mainSignature.returnTy?.not(context, Primitives.NIL)?.let {
                    TyMultipleResults.flatten(context, it)
                }

                if (returnTy is TyMultipleResults) {
                    ty = returnTy.list.getOrNull(paramIndex) ?: Primitives.UNKNOWN
                } else if (paramIndex == 0) {
                    ty = returnTy
                } else {
                    ty = Primitives.VOID
                }
            }
        }

        is LuaForAStat -> {
            // for param = 1, 2 do end
            ty = Primitives.NUMBER
        }

        is LuaClosureExpr -> {
            /**
             * ---@param processor fun(p1:TYPE):void
             * local function test(processor)
             * end
             *
             * test(function(p1)  end)
             *
             * guess type for p1
             */

            context.withConcreteGenericSupport(true) {
                paramOwner.shouldBe(context)?.let {
                    Ty.eachResolved(context, it) {
                        if (it is ITyFunction) {
                            val paramIndex = paramOwner.getIndexFor(paramDef)
                            val param = it.mainSignature.params?.getOrNull(paramIndex)
                            val paramTy = param?.let {
                                if (it.optional) {
                                    TyUnion.union(context, Primitives.NIL, it.ty)
                                } else {
                                    it.ty
                                }
                            } ?: Primitives.UNKNOWN // TODO: Any vs unknown. Should be unknown

                            ty = TyUnion.union(context, ty, paramTy)
                        }
                    }
                }
            }
        }
    }

    return ty
}
