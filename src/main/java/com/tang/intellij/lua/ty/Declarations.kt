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

import com.intellij.openapi.util.Computable
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.psi.LuaDocTagField
import com.tang.intellij.lua.ext.recursionGuard
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.search.withRecursionGuard

fun infer(element: LuaTypeGuessable?, context: SearchContext): ITy? {
    if (element == null)
        return null
    return SearchContext.infer(element, context)
}

internal fun inferInner(element: LuaTypeGuessable, context: SearchContext): ITy? {
    return when (element) {
        is LuaFuncBodyOwner<*> -> element.infer(context)
        is LuaExpression<*> -> inferExpr(element, context)
        is LuaParamDef -> element.infer(context)
        is LuaLocalDef -> element.infer(context)
        is LuaDocTagField -> element.infer()
        is LuaTableField -> element.infer(context)
        is LuaPsiFile -> inferFile(element, context)
        else -> null
    }
}

fun inferReturnTy(owner: LuaFuncBodyOwner<*>, searchContext: SearchContext): ITy? {
    return owner.stub?.guessReturnTy(searchContext) ?: inferReturnTyInner(owner, searchContext)
}

private fun inferReturnTyInner(owner: LuaFuncBodyOwner<*>, searchContext: SearchContext): ITy? {
    val returnTag = owner.tagReturn

    if (returnTag != null) {
        return returnTag.type
    }

    //infer from return stat
    return withRecursionGuard("inferReturnTyInner", owner) {
        var type: ITy? = Ty.VOID

        owner.acceptChildren(object : LuaRecursiveVisitor() {
            override fun visitReturnStat(o: LuaReturnStat) {
                if (type == null) {
                    return
                }

                val returnTy = guessReturnType(o, searchContext)

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
                        type = type!!.union(it, searchContext)
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
        })
        type
    }
}

private fun LuaParamDef.infer(context: SearchContext): ITy {
    val ty = resolveParamType(this, context)

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
            type = infer(expr, context) ?: Ty.UNKNOWN
        }
    } else {
        val docTy = this.docTy

        val localStat = PsiTreeUtil.getParentOfType(this, LuaLocalDefStat::class.java)

        if (localStat != null) {
            val index = localStat.getIndexFor(this)

            if (docTy != null) {
                return if (docTy is TyMultipleResults) {
                    docTy.list.getOrNull(index) ?: Ty.NIL
                } else docTy
            }

            val exprList = localStat.exprList

            type = if (exprList != null) {
                // TODO: unknown vs. any - should be unknown
                context.withIndex(index, false) {
                    exprList.guessTypeAt(context)
                } ?: Ty.UNKNOWN
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
                return docTy.list.getOrNull(context.index) ?: Ty.NIL
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
    val valueExpr = valueExpr
    return if (valueExpr != null) infer(valueExpr, context) else null
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

private fun inferFile(file: LuaPsiFile, context: SearchContext): ITy {
    return recursionGuard(file, {
        val moduleName = file.getModuleName(context)
        if (moduleName != null)
            TyLazyClass(moduleName)
        else {
            file.returnStatement()?.let {
                context.withIndex(0) { guessReturnType(it, context) }
            } ?: Ty.VOID
        }
    }) ?: Ty.UNKNOWN
}

/**
 * 找参数的类型
 * @param paramDef param name
 * *
 * @param context SearchContext
 * *
 * @return LuaType
 */
private fun resolveParamType(paramDef: LuaParamDef, context: SearchContext): ITy? {
    val stub = paramDef.stub

    val paramName = stub?.name ?: paramDef.name

    val docTy: ITy? = if (stub != null) {
        stub.docTy
    } else {
        // from comment
        val commentOwner = PsiTreeUtil.getParentOfType(paramDef, LuaCommentOwner::class.java)

        if (commentOwner != null) {
            var comment = commentOwner.comment

            if (comment == null) {
                comment = (commentOwner.parent?.parent as? LuaDeclaration)?.comment // Doc comment may appear on declarations
            }

            val docTy = comment?.getParamDef(paramName)?.type

            if (docTy != null)
                return docTy
        }
        null
    }

    if (docTy != null)
        return docTy

    val paramOwner = PsiTreeUtil.getStubOrPsiParentOfType(paramDef, LuaParametersOwner::class.java)

    // 如果是个类方法，则有可能在父类里
    if (paramOwner is LuaClassMethodDefStat) {
        val classType = paramOwner.guessClassType(context)
        val methodName = paramOwner.name
        var set: ITy? = null
        if (classType != null && methodName != null) {
            Ty.processSuperClasses(classType, context) { superType ->
                val superClass = (if (superType is ITyGeneric) superType.base else superType) as? ITyClass
                val superMethod = superClass?.findMember(methodName, context)
                if (superMethod is LuaClassMethod<*>) {
                    val params = superMethod.params//todo : 优化
                    for (param in params) {
                        if (paramName == param.name) {
                            set = param.ty
                            if (set != null) {
                                return@processSuperClasses false
                            }
                        }
                    }
                }
                true
            }
        }

        if (set != null) {
            return set
        }
    }

    // module fun
    // function method(self) end
    if (paramOwner is LuaFuncDefStat && paramName == Constants.WORD_SELF) {
        val moduleName = paramDef.getModuleName(context)
        if (moduleName != null) {
            return TyLazyClass(moduleName)
        }
    }

    //for (iterator support)
    if (paramOwner is LuaForBStat) {
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
            iterator = (exprList?.expressionList?.firstOrNull() as? LuaTypeGuessable)?.guessType(context) as? ITyFunction
        }

        if (iterator != null) {
            var result: ITy = Ty.VOID
            val returnTy = iterator.mainSignature.returnTy?.not(Ty.NIL, context)?.let {
                TyMultipleResults.flatten(context, it)
            }

            if (returnTy == null) {
                return null
            }

            if (returnTy is TyMultipleResults) {
                result = returnTy.list.getOrNull(paramIndex)?.let {
                    result.union(it, context)
                } ?: Ty.UNKNOWN
            } else if (paramIndex == 0) {
                result = result.union(returnTy, context)
            }

            return result
        }
    }
    // for param = 1, 2 do end
    if (paramOwner is LuaForAStat)
        return Ty.NUMBER
    /**
     * ---@param processor fun(p1:TYPE):void
     * local function test(processor)
     * end
     *
     * test(function(p1)  end)
     *
     * guess type for p1
     */
    if (paramOwner is LuaClosureExpr) {
        val shouldBe = paramOwner.shouldBe(context)

        if (shouldBe == null) {
            return null
        }

        var ret: ITy? = null

        Ty.eachResolved(shouldBe, context) {
            if (it is ITyFunction) {
                val paramIndex = paramOwner.getIndexFor(paramDef)
                ret = TyUnion.union(ret, it.mainSignature.getArgTy(paramIndex), context)
            }
        }

        return ret
    }

    return null
}
