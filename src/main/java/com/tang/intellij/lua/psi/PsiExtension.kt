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

package com.tang.intellij.lua.psi

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.tang.intellij.lua.comment.LuaCommentUtil
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.comment.psi.LuaDocTagGenericList
import com.tang.intellij.lua.comment.psi.api.LuaComment
import com.tang.intellij.lua.comment.psi.impl.LuaDocTagTypeImpl
import com.tang.intellij.lua.lang.type.LuaString
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassMemberIndex
import com.tang.intellij.lua.ty.*

/**
 * 1.
 * ---@type MyClass
 * local a = {}
 *
 * this table should be `MyClass`
 *
 * 2.
 * ---@param callback fun(sender: any, type: string):void
 * local function addListener(type, callback)
 *      ...
 * end
 *
 * addListener(function() end)
 *
 * this closure should be `fun(sender: any, type: string):void`
 *
 * 3.
 * ---@class MyClass
 * ---@field foo Bar
 * local a = {}
 *
 * ---@type MyClass
 * local tbl = {
 *     foo = ?? -- foo should be type of `Bar`
 * }
 *
 * 4.
 * ---@type fun(a: number, b: string): void
 * return function(a, b) end
 */
private fun LuaExpression<*>.shouldBeInternal(context: SearchContext): ITy? {
    val p1 = parent
    if (p1 is LuaExprList) {
        val p2 = p1.parent
        if (p2 is LuaAssignStat) {
            val receiver = p2.varExprList.getExpressionAt(0)
            if (receiver != null)
                return infer(receiver, context)
        } else if (p2 is LuaLocalDefStat) {
            val receiver = p2.localDefList.firstOrNull()
            if (receiver != null)
                return infer(receiver, context)
        } else if (p2 is LuaReturnStat) {
            val returnType = PsiTreeUtil.getChildrenOfTypeAsList(p2.comment, LuaDocTagTypeImpl::class.java).firstOrNull()

            if (returnType != null) {
                return if (context.supportsMultipleResults) {
                    returnType.getType()
                } else returnType.getType(context.index)
            }

            val tagReturn = PsiTreeUtil.getParentOfType(p2, LuaFuncBodyOwner::class.java)?.tagReturn

            if (tagReturn != null) {
                return if (context.supportsMultipleResults) {
                    tagReturn.getType()
                } else {
                    TyMultipleResults.getResult(context, tagReturn.getType(), context.index)
                }
            }
        }
    } else if (p1 is LuaArgs) {
        val p2 = p1.parent
        if (p2 is LuaCallExpr) {
            val idx = p1.getIndexFor(this)
            val fTy = infer(p2.expression, context)

            if (fTy != null) {
                var ret: ITy = Ty.VOID
                Ty.eachResolved(fTy, context) {
                    if (it is ITyFunction) {
                        var sig = it.matchSignature(context, p2)?.signature ?: it.mainSignature
                        val substitutor = p2.createSubstitutor(sig, context)
                        sig = sig.substitute(substitutor)
                        ret = ret.union(sig.getParamTy(idx), context)
                    }
                }
                return ret
            }
        }
    } else if (p1 is LuaTableField) {
        val tbl = p1.parent

        if (tbl is LuaTableExpr) {
            val tyTbl = tbl.shouldBe(context)

            if (tyTbl == null) {
                return null
            }

            var fieldType: ITy = Ty.VOID

            Ty.eachResolved(tyTbl, context) { type ->
                val classFieldTy = p1.name?.let {
                    type.guessMemberType(it, context)
                } ?: p1.guessIndexType(context)?.let {
                    type.guessIndexerType(it, context)
                }

                if (classFieldTy != null) {
                    fieldType = fieldType.union(classFieldTy, context)
                }
            }

            return fieldType
        }
    }
    return null
}

fun LuaExpression<*>.shouldBe(context: SearchContext): ITy? {
    return shouldBeInternal(context)?.let {
        TyAliasSubstitutor.substitute(it, context)
    }
}

fun LuaLocalDefStat.getIndexFor(localDef: LuaLocalDef): Int {
    val stub = localDef.stub
    return if (stub != null) {
        stub.childrenStubs.indexOf(localDef.stub)
    } else {
        localDefList.indexOf(localDef)
    }
}

val LuaLocalDef.docTy: ITy? get() {
    val stub = stub
    if (stub != null)
        return stub.docTy

    val localStat = PsiTreeUtil.getParentOfType(this, LuaLocalDefStat::class.java)
    return localStat?.comment?.ty
}

fun LuaAssignStat.getIndexFor(psi: LuaExpression<*>): Int {
    var idx = 0
    val stub = valueExprList?.stub
    if (stub != null) {
        val children = stub.childrenStubs
        for (i in 0 until children.size) {
            if (psi == children[i].psi) {
                idx = i
                break
            }
        }
    } else {
        LuaPsiTreeUtilEx.processChildren(this.varExprList, Processor{
            if (it is LuaExpression<*>) {
                if (it == psi)
                    return@Processor false
                idx++
            }
            return@Processor true
        })
    }
    return idx
}

fun LuaAssignStat.getLastIndex(): Int {
    val stub = valueExprList?.stub
    if (stub != null) {
        return stub.childrenStubs.size
    }
    var count = 0
    LuaPsiTreeUtilEx.processChildren(this.varExprList, Processor{
        if (it is LuaExpression<*>) {
            count++
        }
        return@Processor true
    })
    return count - 1
}

fun LuaAssignStat.getExpressionAt(index:Int) : LuaExpression<*>? {
    val list = this.varExprList.expressionList
    return list.getOrNull(index)
}

fun LuaArgs.getIndexFor(psi: LuaExpression<*>): Int {
    if (this is LuaSingleArg)
        return 0
    var idx = 0
    LuaPsiTreeUtilEx.processChildren(this, Processor {
        if (it is LuaExpression<*>) {
            if (it == psi)
                return@Processor false
            idx++
        }
        return@Processor true
    })
    return idx
}

val LuaExprList.expressionStubList: List<LuaExpression<*>> get() {
    return PsiTreeUtil.getStubChildrenOfTypeAsList(this, LuaExpression::class.java)
}

fun LuaExprList.getExpressionAt(idx: Int): LuaExpression<*>? {
    return expressionStubList.getOrNull(idx)
}

fun LuaExprList.guessType(context: SearchContext): ITy? {
    val exprList = expressionStubList
    return if (exprList.size == 1) {
        exprList.first().guessType(context)
    } else {
        val list = mutableListOf<ITy>()
        var variadic = false
        exprList.forEachIndexed { index, luaExpr ->
            val ty = luaExpr.guessType(context) ?: Ty.UNKNOWN

            if (ty is TyMultipleResults) {
                if (index == exprList.size - 1) {
                    list.addAll(ty.list)
                    variadic = ty.variadic
                } else {
                    list.addAll(ty.list.take(1))
                }
            } else {
                list.add(ty)
            }
        }
        TyMultipleResults(list, variadic)
    }
}

fun LuaParametersOwner<*>.getIndexFor(paramDef: LuaParamDef): Int {
    val list = this.paramDefList
    list?.indices?.filter { list[it].name == paramDef.name }?.forEach { return it }
    return 0
}

val LuaParamDef.owner: LuaParametersOwner<*>
    get() = PsiTreeUtil.getParentOfType(this, LuaParametersOwner::class.java)!!

val LuaFuncBodyOwner<*>.overloads: Array<IFunSignature> get() {
    return stub?.overloads ?: (this as? LuaCommentOwner)?.comment?.overloads ?: arrayOf()
}

val LuaFuncBodyOwner<*>.genericParams: Array<TyGenericParameter>? get() {
    val stub = this.stub

    if (stub != null) {
        return stub.genericParams
    }

    val list = mutableListOf<TyGenericParameter>()

    if (this is LuaCommentOwner) {
        comment?.findTags(LuaDocTagGenericList::class.java)?.forEach {
            it.genericDefList.forEach { genericDef ->
                list.add(TyGenericParameter(genericDef))
            }
        }
    }

    return list.toTypedArray()
}

enum class LuaLiteralKind {
    String,
    Bool,
    Number,
    Nil,
    Varargs,
    Unknown;

    companion object {
        fun toEnum(ID: Byte): LuaLiteralKind {
            return LuaLiteralKind.values().find { it.ordinal == ID.toInt() } ?: Unknown
        }
    }
}

val LuaLiteralExpr.kind: LuaLiteralKind get() {
    val stub = this.stub
    if (stub != null)
        return stub.kind

    return when(node.firstChildNode.elementType) {
        LuaTypes.STRING -> LuaLiteralKind.String
        LuaTypes.TRUE -> LuaLiteralKind.Bool
        LuaTypes.FALSE -> LuaLiteralKind.Bool
        LuaTypes.NIL -> LuaLiteralKind.Nil
        LuaTypes.NUMBER -> LuaLiteralKind.Number
        LuaTypes.ELLIPSIS -> LuaLiteralKind.Varargs
        else -> LuaLiteralKind.Unknown
    }
}

/**
 * too larger to write to stub
 */
val LuaLiteralExpr.tooLargerString: Boolean get() {
    return stub?.tooLargerString ?: (stringValue.length >= 1024 * 10)
}

val LuaLiteralExpr.stringValue: String get() {
    val stub = stub
    if (stub != null && !stub.tooLargerString)
        return stub.string ?: ""
    val content = LuaString.getContent(text)
    return content.value
}

val LuaLiteralExpr.boolValue: Boolean get() = text == "true"

val LuaLiteralExpr.numberValue: Float get() {
    val t = text
    if (t.startsWith("0x", true)) {
        return "${t}p0".toFloat()
    }
    return text.toFloat()
}

val LuaComment.docTy: ITy? get() {
    return this.tagType?.getType()
}

val LuaComment.ty: ITy? get() {
    val cls = tagClass?.type
    return cls ?: tagType?.getType()
}

val LuaDocTagClass.overloads: Array<IFunSignature>? get() {
    return LuaCommentUtil.findContainer(this).overloads
}

val LuaDocTagClass.aliasName: String? get() {
    val owner = LuaCommentUtil.findOwner(this)
    when (owner) {
        is LuaAssignStat -> {
            val expr = owner.getExpressionAt(0)
            if (expr is LuaNameExpr)
                return getGlobalTypeName(expr)
        }

        is LuaLocalDefStat -> {
            val expr = owner.exprList?.getExpressionAt(0)
            if (expr is LuaTableExpr)
                return getTableTypeName(expr)
        }
    }
    return null
}

val LuaIndexExpr.brack: Boolean get() {
    val stub = stub
    return stub?.brack ?: (lbrack != null)
}

val LuaIndexExpr.docTy: ITy? get() {
    val stub = stub
    return if (stub != null)
        stub.docTy
    else
        assignStat?.comment?.docTy
}

val LuaIndexExpr.prefixExpression: LuaExpression<*> get() {
    return PsiTreeUtil.getStubChildOfType(this, LuaExpression::class.java)!!
}

val LuaExpression<*>.assignStat: LuaAssignStat? get() {
    val p1 = PsiTreeUtil.getStubOrPsiParent(this)
    if (p1 is LuaVarList) {
        val p2 = PsiTreeUtil.getStubOrPsiParent(p1)
        if (p2 is LuaAssignStat)
            return p2
    }
    return null
}

val LuaNameExpr.docTy: ITy? get() {
    val stub = stub
    if (stub != null)
        return stub.docTy
    return assignStat?.comment?.ty
}

private val KEY_SHOULD_CREATE_STUB = Key.create<CachedValue<Boolean>>("lua.should_create_stub")

// { field = valueExpr }
// { valueExpr }
// { ["field"] = valueExpr }
val LuaTableField.valueExpression: LuaExpression<*>? get() {
    val list = PsiTreeUtil.getStubChildrenOfTypeAsList(this, LuaExpression::class.java)
    return list.lastOrNull()
}

val LuaTableField.shouldCreateStub: Boolean get() =
    CachedValuesManager.getCachedValue(this, KEY_SHOULD_CREATE_STUB) {
        CachedValueProvider.Result.create(innerShouldCreateStub, this)
    }

private val LuaTableField.innerShouldCreateStub: Boolean get() {
    val tableExpr = PsiTreeUtil.getStubOrPsiParentOfType(this, LuaTableExpr::class.java)
    tableExpr ?: return false
    return tableExpr.shouldCreateStub
}

val LuaTableExpr.shouldCreateStub: Boolean get() =
    CachedValuesManager.getCachedValue(this, KEY_SHOULD_CREATE_STUB) {
        CachedValueProvider.Result.create(innerShouldCreateStub, this)
    }

private val LuaTableExpr.innerShouldCreateStub: Boolean get() {
    val pt = parent
    return when (pt) {
        is LuaTableField -> pt.shouldCreateStub
        is LuaExprList -> {
            val ppt = pt.parent
            when (ppt) {
                is LuaArgs-> false
                else-> true
            }
        }
        else-> true
    }
}

val LuaCallExpr.prefixExpression: LuaExpression<*>? get() {
    val expr = this.expression
    if (expr is LuaIndexExpr) {
        return expr.prefixExpression
    }
    return null
}

val LuaCallExpr.argList: List<LuaExpression<*>> get() {
    val args = this.args
    return when (args) {
        is LuaSingleArg -> listOf(args.expression)
        is LuaListArgs -> args.expressionList
        else -> emptyList()
    }
}

val LuaBinaryExpr.left: LuaExpression<*>? get() {
    return PsiTreeUtil.getStubChildOfType(this, LuaExpression::class.java)
}

val LuaBinaryExpr.right: LuaExpression<*>? get() {
    val list = PsiTreeUtil.getStubChildrenOfTypeAsList(this, LuaExpression::class.java)
    return list.getOrNull(1)
}

fun LuaClassMethod<*>.findOverridingMethod(context: SearchContext): LuaClassMethod<*>? {
    val methodName = name ?: return null
    val type = guessClassType(context) ?: return null
    var superMethod: LuaClassMethod<*>? = null
    Ty.processSuperClasses(type, context) { superType ->
        ProgressManager.checkCanceled()
        val superClass = (if (superType is ITyGeneric) superType.base else superType) as? ITyClass
        if (superClass != null) {
            val superTypeName = superClass.className
            superMethod = LuaClassMemberIndex.findMethod(superTypeName, methodName, context)
            superMethod == null
        } else true
    }
    return superMethod
}
