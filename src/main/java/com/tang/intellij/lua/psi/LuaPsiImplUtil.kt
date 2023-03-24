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

@file:Suppress("UNUSED_PARAMETER")

package com.tang.intellij.lua.psi

import com.intellij.icons.AllIcons
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.Computable
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.psi.*
import com.tang.intellij.lua.comment.psi.impl.LuaDocTagTypeImpl
import com.tang.intellij.lua.ext.recursionGuard
import com.tang.intellij.lua.lang.LuaIcons
import com.tang.intellij.lua.lang.type.LuaString
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.LuaClassMemberStub
import com.tang.intellij.lua.ty.*
import java.util.*
import javax.swing.Icon

fun setName(owner: PsiNameIdentifierOwner, name: String): PsiElement {
    val oldId = owner.nameIdentifier
    if (oldId != null) {
        val newId = LuaElementFactory.createIdentifier(owner.project, name)
        oldId.replace(newId)
        return newId
    }
    return owner
}

fun getNameIdentifier(localDef: LuaLocalDef): PsiElement {
    return localDef.firstChild
}

fun getNameIdentifier(paramDef: LuaParamDef): PsiElement {
    return paramDef.firstChild
}

fun getUseScope(localDef: LuaLocalDef): SearchScope {
    return GlobalSearchScope.fileScope(localDef.containingFile)
}

fun getUseScope(paramDef: LuaParamDef): SearchScope {
    return GlobalSearchScope.fileScope(paramDef.containingFile)
}

fun getReferences(element: LuaPsiElement): Array<PsiReference> {
    return ReferenceProvidersRegistry.getReferencesFromProviders(element, PsiReferenceService.Hints.NO_HINTS)
}

fun getNameIdentifier(classMethodDefStat: LuaClassMethodDefStat): PsiElement? {
    return classMethodDefStat.classMethodName.id
}

fun getName(classMethodDefStat: LuaClassMethodDefStat): String? {
    val stub = classMethodDefStat.stub
    if (stub != null) {
        return stub.name
    }

    return getName(classMethodDefStat as PsiNameIdentifierOwner)
}

fun isStatic(classMethodDefStat: LuaClassMethodDefStat): Boolean {
    val stub = classMethodDefStat.stub
    if (stub != null)
        return stub.isStatic

    return classMethodDefStat.classMethodName.dot != null
}

fun getPresentation(classMethodDefStat: LuaClassMethodDefStat): ItemPresentation {
    return object : ItemPresentation {
        override fun getPresentableText(): String? {
            val type = classMethodDefStat.guessParentClass(SearchContext.get(classMethodDefStat.project))
            if (type != null) {
                val c = if (classMethodDefStat.isStatic) "." else ":"
                return type.displayName + c + classMethodDefStat.name + classMethodDefStat.paramSignature
            }
            return classMethodDefStat.name!! + classMethodDefStat.paramSignature
        }

        override fun getLocationString(): String {
            return classMethodDefStat.containingFile.name
        }

        override fun getIcon(b: Boolean): Icon? {
            return LuaIcons.CLASS_METHOD
        }
    }
}

fun guessTypeAt(list: LuaExprList, context: SearchContext): ITy? {
    val exprList = list.expressionStubList

    val expr = exprList.getOrNull(context.index) ?: exprList.lastOrNull()
    if (expr != null) {
        //local function getValues12() return 1, 2 end
        //local function getValues34() return 3, 4 end
        //local a, b = getValues12() -- a = 1, b = 2
        //local a, b, c = getValues12(), 3, 4 --a = 1, b = 3, c =  4
        //local a, b, c = getValues12(), getValue34() --a = 1, b = 3, c =  4
        var index = context.index
        if (exprList.size > 1) {
            val nameSize = context.index + 1
            index = if (nameSize > exprList.size) {
                nameSize - exprList.size
            } else 0
        }
        return context.withIndex(index, context.supportsMultipleResults) {
            expr.guessType(context)
        }
    }
    return null
}

/**
 * 寻找对应的类
 * @param classMethodDefStat def
 * *
 * @return LuaType
 */
fun guessParentType(classMethodDefStat: LuaClassMethodDefStat, context: SearchContext): ITy {
    /*val stub = classMethodDefStat.stub
    var type: ITy = Primitives.UNKNOWN
    if (stub != null) {
        stub.classes.forEach {
           type = type.union(it)
        }
    } else {
        val expr = classMethodDefStat.classMethodName.expr
        val ty = expr.guessType(ctx)
        val perfect = TyUnion.getPerfectClass(ty)
        if (perfect is ITyClass)
            type = perfect
    }*/

    val expr = classMethodDefStat.classMethodName.expression
    return recursionGuard(classMethodDefStat, Computable {
        expr.guessType(context)?.let {
            TyUnion.getPerfectClass(it)
        }
    }) ?: Primitives.UNKNOWN
}

fun guessParentType(luaClosureExpr: LuaClosureExpr, context: SearchContext): ITy {
    (luaClosureExpr.parent as? LuaTableField)?.let { tableField ->
        return tableField.guessParentType(context)
    }

    (luaClosureExpr.parent.parent as? LuaAssignStat)?.let { assignment ->
        val index = assignment.valueExprList?.expressionList?.indexOf(luaClosureExpr)

        if (index != null) {
            val expr = assignment.varExprList.expressionList.getOrNull(index)

            if (expr is LuaIndexExpr) {
                return expr.guessParentType(context)
            }
        }
    }

    return Primitives.UNKNOWN
}

fun guessParentType(luaLocalFuncDefStat: LuaLocalFuncDefStat, context: SearchContext): ITy {
    return Primitives.UNKNOWN
}

fun getNameIdentifier(funcDefStat: LuaFuncDefStat): PsiElement? {
    return funcDefStat.id
}

fun getName(funcDefStat: LuaFuncDefStat): String? {
    val stub = funcDefStat.stub
    if (stub != null)
        return stub.name
    return getName(funcDefStat as PsiNameIdentifierOwner)
}

fun getPresentation(funcDefStat: LuaFuncDefStat): ItemPresentation {
    return object : ItemPresentation {
        override fun getPresentableText(): String? {
            return funcDefStat.name?.let { it + funcDefStat.paramSignature }
        }

        override fun getLocationString(): String {
            return funcDefStat.containingFile.name
        }

        override fun getIcon(b: Boolean): Icon {
            return AllIcons.Nodes.Function
        }
    }
}

fun guessParentType(funcDefStat: LuaFuncDefStat, searchContext: SearchContext): ITyClass {
    return TyClass.G
}

/**
 * 猜出前面的类型
 * @param callExpr call expr
 * *
 * @return LuaType
 */
fun guessParentType(callExpr: LuaCallExpr, context: SearchContext): ITy? {
    return callExpr.expression.guessType(context)
}

/**
 * 获取第一个字符串参数
 * @param callExpr callExpr
 * *
 * @return String PsiElement
 */
fun getFirstStringArg(callExpr: LuaCallExpr): PsiElement? {
    val args = callExpr.args
    var path: PsiElement? = null

    when (args) {
        is LuaSingleArg -> {
            val expr = args.expression
            if (expr is LuaLiteralExpr) path = expr
        }
        is LuaListArgs -> args.expressionList.let { list ->
            if (list.isNotEmpty() && list[0] is LuaLiteralExpr) {
                val valueExpr = list[0] as LuaLiteralExpr
                if (valueExpr.kind == LuaLiteralKind.String)
                    path = valueExpr
            }
        }
    }
    return path
}

fun isMethodDotCall(callExpr: LuaCallExpr): Boolean {
    val expr = callExpr.expression
    if (expr is LuaNameExpr)
        return true
    return expr is LuaIndexExpr && expr.colon == null
}

fun isMethodColonCall(callExpr: LuaCallExpr): Boolean {
    val expr = callExpr.expression
    return expr is LuaIndexExpr && expr.colon != null
}

fun isFunctionCall(callExpr: LuaCallExpr): Boolean {
    return callExpr.expression is LuaNameExpr
}

fun guessParentType(indexExpr: LuaIndexExpr, context: SearchContext): ITy {
    val expr = PsiTreeUtil.getStubChildOfType(indexExpr, LuaExpression::class.java)
    return expr?.guessType(context) ?: Primitives.UNKNOWN
}

fun getNameIdentifier(indexExpr: LuaIndexExpr): PsiElement? {
    val id = indexExpr.id
    if (id != null)
        return id
    return indexExpr.idExpr
}

fun getPresentation(indexExpr: LuaIndexExpr): ItemPresentation {
    return object : ItemPresentation {
        override fun getPresentableText(): String? {
            return indexExpr.name
        }

        override fun getLocationString(): String {
            return indexExpr.containingFile.name
        }

        override fun getIcon(b: Boolean): Icon? {
            return LuaIcons.CLASS_FIELD
        }
    }
}

/**
 * x in y[x]
 */
fun getIdExpr(indexExpr: LuaIndexExpr): LuaExpression<*>? {
    val bracket = indexExpr.lbrack
    if (bracket != null) {
        val nextLeaf = PsiTreeUtil.getNextSiblingOfType(bracket, LuaExpression::class.java)
        if (nextLeaf is LuaExpression<*>)
            return nextLeaf
    }
    return null
}

fun guessIndexType(indexExpr: LuaIndexExpr, context: SearchContext): ITy? {
    val stubIdTy = indexExpr.stub?.idTy

    if (stubIdTy != null) {
        return stubIdTy
    }

    val idExpr = indexExpr.idExpr

    return if (idExpr != null) {
        SearchContext.infer(idExpr)
    } else null
}

fun getName(indexExpr: LuaIndexExpr): String? {
    val stub = indexExpr.stub
    if (stub != null)
        return stub.name

    // var.name
    val id = indexExpr.id
    if (id != null)
        return id.text

    // var['name']
    val idExpr = indexExpr.idExpr as? LuaLiteralExpr
    if (idExpr != null && idExpr.kind == LuaLiteralKind.String) {
        return LuaString.getContent(idExpr.text).value
    }

    return null
}

fun setName(indexExpr: LuaIndexExpr, name: String): PsiElement {
    if (indexExpr.id != null)
        return setName(indexExpr as PsiNameIdentifierOwner, name)
    val idExpr = indexExpr.idExpr as? LuaLiteralExpr
    if (idExpr != null && idExpr.kind == LuaLiteralKind.String) {
        val text = idExpr.text
        val content = LuaString.getContent(text)
        val newText = text.substring(0, content.start) + name + text.substring(content.end)
        val newId = LuaElementFactory.createLiteral(indexExpr.project, newText)
        return idExpr.replace(newId)
    }
    return indexExpr
}

fun findField(table: LuaTableExpr, fieldName: String): LuaTableField? {
    return table.tableFieldList.firstOrNull { fieldName == it.name }
}

fun getParamDefList(funcBodyOwner: LuaFuncBodyOwner<*>): List<LuaParamDef> {
    val funcBody = funcBodyOwner.funcBody
    return funcBody?.paramDefList ?: emptyList()
}

fun getParamDefList(forAStat: LuaForAStat): List<LuaParamDef> {
    val list = ArrayList<LuaParamDef>()
    list.add(forAStat.paramDef)
    return list
}

fun guessReturnType(owner: LuaFuncBodyOwner<*>, context: SearchContext): ITy? {
    return inferReturnTy(context, owner)
}

fun getTagReturn(owner: LuaFuncBodyOwner<*>): LuaDocTagReturn? {
    return (owner as? LuaCommentOwner)?.comment?.tagReturn
}

fun getTagVararg(owner: LuaFuncBodyOwner<*>): LuaDocTagVararg? {
    return (owner as? LuaCommentOwner)?.comment?.tagVararg
}

fun getVarargTy(owner: LuaFuncBodyOwner<*>): ITy? {
    return owner.stub?.varargTy ?: owner.funcBody?.ellipsis?.let {
        return owner.tagVararg?.type ?: Primitives.UNKNOWN
    }
}

fun getParams(owner: LuaFuncBodyOwner<*>): Array<LuaParamInfo> {
    return owner.stub?.params ?: getParamsInner(owner)
}

private fun getParamsInner(funcBodyOwner: LuaFuncBodyOwner<*>): Array<LuaParamInfo> {
    val comment = funcBodyOwner.comment
    val paramNameList = funcBodyOwner.paramDefList

    if (paramNameList != null) {
        val list = mutableListOf<LuaParamInfo>()

        for (i in paramNameList.indices) {
            val name = paramNameList[i].text
            val paramDef = comment?.let {
                comment.getParamDef(name)
            }
            list.add(LuaParamInfo(name, paramDef?.type, paramDef?.optional != null))
        }

        return list.toTypedArray()
    }

    return emptyArray()
}

fun getParamSignature(params: Array<out LuaParamInfo>): String {
    val list = arrayOfNulls<String>(params.size)
    for (i in params.indices) {
        val lpi = params[i]
        list[i] = lpi.name
    }
    return "(" + list.joinToString(", ") + ")"
}

fun getParamSignature(funcBodyOwner: LuaFuncBodyOwner<*>): String {
    return getParamSignature(funcBodyOwner.params)
}

fun getName(localFuncDefStat: LuaLocalFuncDefStat): String? {
    val stub = localFuncDefStat.stub
    if (stub != null)
        return stub.name
    return getName(localFuncDefStat as PsiNameIdentifierOwner)
}

fun getNameIdentifier(localFuncDefStat: LuaLocalFuncDefStat): PsiElement? {
    return localFuncDefStat.id
}

fun getUseScope(localFuncDefStat: LuaLocalFuncDefStat): SearchScope {
    return GlobalSearchScope.fileScope(localFuncDefStat.containingFile)
}

fun getName(localDef: LuaLocalDef): String {
    val stub = localDef.stub
    if (stub != null)
        return stub.name
    return localDef.id.text
}

fun getName(paramDef: LuaParamDef): String {
    val stub = paramDef.stub
    if (stub != null)
        return stub.name
    return paramDef.id.text
}

fun getName(identifierOwner: PsiNameIdentifierOwner): String? {
    val id = identifierOwner.nameIdentifier
    return id?.text
}

fun getTextOffset(localFuncDefStat: PsiNameIdentifierOwner): Int {
    val id = localFuncDefStat.nameIdentifier
    if (id != null) return id.textOffset
    return localFuncDefStat.node.startOffset
}

fun getNameIdentifier(tableField: LuaTableField): PsiElement? {
    val id = tableField.id
    if (id != null)
        return id
    return tableField.idExpr
}

fun guessParentType(tableField: LuaTableField, context: SearchContext): ITy {
    // We attempt to detect metatable literals, and within the table treat the owner (self) as return value of __call
    return ((PsiTreeUtil.getParentOfType(tableField, LuaStatement::class.java) as? LuaExprStat)?.expression as? LuaCallExpr)?.let { callExpr ->
        val argList = callExpr.argList

        // Note: We're not resolving the call expression because we want this to work in "dumb mode" (i.e. during indexing). If the user
        //       has a "setmetatable" in scope that isn't functionally equivalent to Lua's setmetatable... too bad.
        if (argList.size == 2 && argList[1] == tableField.parent && callExpr.nameExpr?.text == Constants.FUNCTION_SETMETATABLE) {
            val callMetamethod = (tableField.parent as LuaTableExpr).findField(Constants.METAMETHOD_CALL)?.valueExpr as? LuaFuncBodyOwner<*>
            val returnClass = callMetamethod?.guessReturnType(context) as? ITyClass
            if (returnClass != null && returnClass.className != Constants.WORD_SELF && !isSelfClass(returnClass)) {
                returnClass
            } else {
                argList[0].guessType(context) as? ITyClass
            }
        } else {
            null
        }
    } ?: PsiTreeUtil.getParentOfType(tableField, LuaTableExpr::class.java)?.guessType(context) ?: Primitives.UNKNOWN
}

fun guessIndexType(tableField: LuaTableField, context: SearchContext): ITy? {
    if (tableField.name != null) {
        return null
    }

    val indexTy = tableField.stub?.indexTy ?: tableField.lbrack?.let {
        tableField.idExpr?.guessType(context)
    }

    if (indexTy != null || tableField.lbrack != null) {
        return indexTy
    } else {
        var fieldIndex = 0
        val siblingFields = tableField.parent.children

        for (i in 0 until siblingFields.size) {
            val siblingField = siblingFields[i]

            if (siblingField is LuaTableField && siblingField.lbrack == null && siblingField.name == null) {
                fieldIndex += 1
            }

            if (siblingField == tableField) {
                break
            }
        }

        return TyPrimitiveLiteral.getTy(TyPrimitiveKind.Number, fieldIndex.toString())
    }
}

fun guessType(tableField: LuaTableField, context: SearchContext): ITy? {
    tableField.stub?.valueTy?.let {
        return it
    }

    val valueTy = tableField.comment?.docTy ?: tableField.valueExpr?.guessType(context)

    return if (tableField.name != null || tableField.lbrack != null) {
        if (valueTy is TyMultipleResults) {
            valueTy.list.first()
        } else {
            valueTy
        }
    } else {
        var fieldIndex = 0
        val siblingFields = tableField.parent.children

        for (i in 0 until siblingFields.size) {
            val siblingField = siblingFields[i]

            if (siblingField is LuaTableField && siblingField.lbrack == null && siblingField.name == null) {
                fieldIndex += 1
            }

            if (siblingField == tableField) {
                break
            }
        }

        if (valueTy is TyMultipleResults) {
            if (fieldIndex + 1 == siblingFields.size) valueTy else valueTy.list.first()
        } else valueTy
    }
}

fun getName(tableField: LuaTableField): String? {
    val stub = tableField.stub

    if (stub != null) {
        return stub.name
    }

    return tableField.id?.text
}

fun getPresentation(tableField: LuaTableField): ItemPresentation {
    return object : ItemPresentation {
        override fun getPresentableText(): String? {
            return tableField.name
        }

        override fun getLocationString(): String {
            return tableField.containingFile.name
        }

        override fun getIcon(b: Boolean): Icon? {
            return LuaIcons.CLASS_FIELD
        }
    }
}

/**
 * xx['id']
 */
fun getIdExpr(tableField: LuaTableField): LuaExpression<*>? {
    val isIndexExpression = tableField.stub?.isIndexExpression ?: tableField.lbrack != null
    return if (isIndexExpression) {
        PsiTreeUtil.getStubChildOfType(tableField, LuaExpression::class.java)
    } else null
}

fun getValueExpr(tableField: LuaTableField): LuaExpression<*>? {
    val isIndexExpression = tableField.stub?.isIndexExpression ?: tableField.lbrack != null
    return if (isIndexExpression) {
        PsiTreeUtil.getStubChildrenOfTypeAsList(tableField, LuaExpression::class.java).getOrNull(1)
    } else PsiTreeUtil.getStubChildOfType(tableField, LuaExpression::class.java)
}

fun getIndexType(tableField: LuaTableField): LuaDocTy? {
    return null
}

fun toString(stubElement: StubBasedPsiElement<out StubElement<*>>): String {
    return "STUB:[" + stubElement.javaClass.simpleName + "]"
}

fun getPresentation(nameExpr: LuaNameExpr): ItemPresentation {
    return object : ItemPresentation {
        override fun getPresentableText(): String {
            return nameExpr.name
        }

        override fun getLocationString(): String {
            return nameExpr.containingFile.name
        }

        override fun getIcon(b: Boolean): Icon? {
            return LuaIcons.CLASS_FIELD
        }
    }
}

fun getNameIdentifier(ref: LuaNameExpr): PsiElement {
    return ref.id
}

fun getName(nameExpr: LuaNameExpr): String {
    val stub = nameExpr.stub
    if (stub != null)
        return stub.name
    return nameExpr.id.text
}

fun getType(returnStat: LuaReturnStat): ITy? {
    val stub = returnStat.stub

    if (stub != null) {
        val docTy = stub.docTy

        if (docTy != null) {
            return docTy
        }
    } else if (returnStat.comment != null) {
        val returnType = PsiTreeUtil.getChildrenOfTypeAsList(returnStat.comment, LuaDocTagTypeImpl::class.java).firstOrNull()
        return returnType?.getType()
    }

    return null
}

fun guessReturnType(returnStat: LuaReturnStat, context: SearchContext): ITy? {
    val docTy = returnStat.type

    if (docTy != null) {
        return if (context.supportsMultipleResults) {
            docTy
        } else if (docTy is TyMultipleResults) {
            docTy.list.getOrNull(context.index)
        } else {
            docTy
        }
    }

    val returnExpr = returnStat.exprList

    if (returnExpr != null) {
        return if (context.supportsMultipleResults) {
            returnExpr.guessType(context)
        } else {
            returnExpr.guessTypeAt(context)
        }
    }

    return Primitives.VOID
}

fun getNameIdentifier(label: LuaLabelStat): PsiElement? {
    return label.id
}

fun getVisibility(member: LuaPsiTypeMember): Visibility {
    if (member is StubBasedPsiElement<*>) {
        val stub = member.stub
        if (stub is LuaClassMemberStub) {
            return stub.visibility
        }
    }
    if (member is LuaCommentOwner) {
        member.comment?.let { comment ->
            PsiTreeUtil.getChildOfType(comment, LuaDocAccessModifier::class.java)?.let {
                return Visibility.get(it.text)
            }
        }
    }
    return Visibility.PUBLIC
}

fun getVisibility(classMethodDefStat: LuaClassMethodDefStat): Visibility {
    return getVisibility(classMethodDefStat as LuaPsiTypeMember)
}

fun getExpression(element: StubBasedPsiElement<*>): LuaExpression<*>? {
    return PsiTreeUtil.getStubChildOfType(element, LuaExpression::class.java)!!
}

fun getExpression(callExpr: LuaCallExpr): LuaExpression<*> {
    return PsiTreeUtil.getStubChildOfType(callExpr, LuaExpression::class.java)!!
}

fun getNameExpr(classMethodName: LuaClassMethodName): LuaNameExpr {
    return PsiTreeUtil.getStubChildOfType(classMethodName, LuaNameExpr::class.java)!!
}

fun getExpression(classMethodName: LuaClassMethodName): LuaExpression<*> {
    return PsiTreeUtil.getStubChildOfType(classMethodName, LuaExpression::class.java)!!
}

fun getExpression(exprStat: LuaExprStat): LuaExpression<*> {
    return PsiTreeUtil.getStubChildOfType(exprStat, LuaExpression::class.java)!!
}

fun getExpression(singleArg: LuaSingleArg): LuaExpression<*> {
    return PsiTreeUtil.getStubChildOfType(singleArg, LuaExpression::class.java)!!
}

fun getExpressionList(element: StubBasedPsiElement<*>): List<LuaExpression<*>> {
    return PsiTreeUtil.getStubChildrenOfTypeAsList(element, LuaExpression::class.java)
}

fun getOperationType(element: LuaBinaryExpr): IElementType {
    return element.stub?.opType ?: element.binaryOp.firstChild.node.elementType
}

fun isDeprecated(member: LuaPsiTypeMember): Boolean {
    if (member is StubBasedPsiElement<*>) {
        val stub = member.stub

        if (stub is LuaClassMemberStub) {
            return stub.isDeprecated
        }
    }

    return (member as? LuaCommentOwner)?.comment?.isDeprecated == true
}

fun isExplicitlyTyped(member: LuaPsiTypeMember): Boolean {
    if (member is StubBasedPsiElement<*>) {
        val stub = member.stub

        if (stub is LuaClassMemberStub) {
            return stub.isExplicitlyTyped
        }
    }

    if (member !is LuaExpression<*>) {
        return false
    }

    val assignStat = member.assignStat
    val comment = assignStat?.comment

    return if (comment != null) {
        val tagType = comment.tagType

        if (tagType != null) {
            val docTyCount = tagType.typeList?.tyList?.size
            docTyCount != null && assignStat.getIndexFor(member) < docTyCount
        } else {
            val valueExpression = assignStat.getExpressionAt(assignStat.getIndexFor(member))
            valueExpression is LuaFuncBodyOwner<*> && comment.isFunctionImplementation
        }
    } else {
        false
    }
}

fun isExplicitlyTyped(luaClassMethod: LuaTypeMethod<*>): Boolean {
    val stub = luaClassMethod.stub

    if (stub is LuaClassMemberStub<*>) {
        return stub.isExplicitlyTyped
    }

    return (luaClassMethod as? LuaCommentOwner)?.comment?.isFunctionImplementation == true
}

fun isExplicitlyTyped(luaTableField: LuaTableField): Boolean {
    return luaTableField.stub?.isExplicitlyTyped ?: luaTableField.comment?.tagType != null
}

fun isExplicitlyTyped(luaIndexExpr: LuaIndexExpr): Boolean {
    val assignStat = luaIndexExpr.assignStat
    val comment = assignStat?.comment

    return if (comment != null) {
        val tagType = comment.tagType

        if (tagType != null) {
            val docTyCount = tagType.typeList?.tyList?.size
            docTyCount != null && assignStat.getIndexFor(luaIndexExpr) <= docTyCount
        } else {
            val expr = assignStat.getExpressionAt(assignStat.getIndexFor(luaIndexExpr))
            return expr is LuaFuncBodyOwner<*> && comment.isFunctionImplementation
        }
    } else {
        false
    }
}
