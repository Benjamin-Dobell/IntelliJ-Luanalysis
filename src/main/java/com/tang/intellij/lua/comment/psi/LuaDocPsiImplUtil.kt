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

package com.tang.intellij.lua.comment.psi

import com.intellij.icons.AllIcons
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.LuaCommentUtil
import com.tang.intellij.lua.comment.reference.LuaTypeReference
import com.tang.intellij.lua.comment.reference.LuaDocParamNameReference
import com.tang.intellij.lua.comment.reference.LuaDocSeeReference
import com.tang.intellij.lua.lang.type.LuaNumber
import com.tang.intellij.lua.lang.type.LuaString
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.search.withRecursionGuard
import com.tang.intellij.lua.ty.*
import javax.swing.Icon

/**

 * Created by TangZX on 2016/11/24.
 */
fun getReference(paramNameRef: LuaDocParamNameRef): PsiReference {
    return LuaDocParamNameReference(paramNameRef)
}

fun getReference(docTypeRef: LuaDocTypeRef): PsiReference {
    return LuaTypeReference(docTypeRef)
}

fun getName(docTypeRef: LuaDocTypeRef): String {
    return docTypeRef.id?.text ?: "table"
}

fun resolveType(docTypeRef: LuaDocTypeRef, context: SearchContext): ITy {
    if (docTypeRef.name == Constants.WORD_TABLE) {
        return Primitives.TABLE
    } else if (docTypeRef.name == Constants.WORD_SELF) {
        val contextClass = LuaPsiTreeUtil.findContextClass(docTypeRef, context) as? ITyClass
        return if (contextClass != null) TyClass.createSelfType(contextClass) else Primitives.UNKNOWN
    }

    return LuaScopedTypeTree.get(docTypeRef.containingFile).findName(context, docTypeRef, docTypeRef.text)?.type ?: Ty.create(docTypeRef.text)
}

fun getName(identifierOwner: PsiNameIdentifierOwner): String? {
    val id = identifierOwner.nameIdentifier
    return id?.text
}

fun setName(identifierOwner: PsiNameIdentifierOwner, newName: String): PsiElement {
    val oldId = identifierOwner.nameIdentifier
    if (oldId != null) {
        val newId = LuaElementFactory.createDocIdentifier(identifierOwner.project, newName)
        oldId.replace(newId)
        return newId
    }
    return identifierOwner
}

fun getTextOffset(identifierOwner: PsiNameIdentifierOwner): Int {
    val id = identifierOwner.nameIdentifier
    return id?.textOffset ?: identifierOwner.node.startOffset
}

fun getNameIdentifier(tagField: LuaDocTagField): PsiElement? {
    return tagField.id
}

fun getNameIdentifier(tagClass: LuaDocTagClass): PsiElement {
    return tagClass.id
}

fun getIndexType(tagField: LuaDocTagField): LuaDocTy? {
    return if (tagField.lbrack != null) tagField.tyList.firstOrNull() else null
}

fun getValueType(tagField: LuaDocTagField): LuaDocTy? {
    return tagField.tyList.getOrNull(if (tagField.lbrack != null) 1 else 0)
}

fun guessType(tagField: LuaDocTagField, context: SearchContext): ITy {
    val stub = tagField.stub
    if (stub != null)
        return stub.valueTy
    return tagField.valueType?.getType() ?: Primitives.UNKNOWN
}

fun guessParentType(tagField: LuaDocTagField, context: SearchContext): ITy {
    val parent = tagField.parent
    val classDef = PsiTreeUtil.findChildOfType(parent, LuaDocTagClass::class.java)
    return classDef?.type ?: Primitives.UNKNOWN
}

fun getVisibility(tagField: LuaDocTagField): Visibility {
    val stub = tagField.stub
    if (stub != null)
        return stub.visibility

    val v = tagField.accessModifier?.let { Visibility.get(it.text) }
    return v ?: Visibility.PUBLIC
}

/**
 * 猜测参数的类型
 * @param tagParamDec 参数定义
 * *
 * @return 类型集合
 */
fun getType(tagParamDec: LuaDocTagParam): ITy {
    return tagParamDec.ty?.getType()?.let { ty ->
        val substitutor = SearchContext.withDumb(tagParamDec.project, null) { context ->
            LuaCommentUtil.findContainer(tagParamDec).createSubstitutor(context)
        }

        if (substitutor != null) {
            ty.substitute(substitutor)
        } else ty
    } ?: Primitives.UNKNOWN
}

fun getType(vararg: LuaDocTagVararg): ITy {
    return vararg.ty?.getType() ?: Primitives.UNKNOWN
}

fun getType(vararg: LuaDocVarargParam): ITy {
    return vararg.ty?.getType() ?: Primitives.UNKNOWN
}

fun getType(returnList: LuaDocReturnList): ITy {
    val list = returnList.typeList.tyList.map { it.getType() }
    val variadic = returnList.varreturn != null

    return if (list.size == 1 && !variadic) {
        list.first()
    } else {
        TyMultipleResults(list, variadic)
    }
}

private fun getReturnType(functionReturnType: LuaDocFunctionReturnType): ITy? {
    return SearchContext.withDumb(functionReturnType.project, null) { context ->
        functionReturnType.returnListList.fold(null as ITy?) { returnTy, returnList ->
            TyUnion.union(returnTy, getType(returnList), context)
        }
    }
}

fun getType(tagReturn: LuaDocTagReturn): ITy {
    return tagReturn.functionReturnType?.let { getReturnType(it) } ?: Primitives.VOID
}

/**
 * 优化：从stub中取名字
 * @param tagClass LuaDocClassDef
 * *
 * @return string
 */
fun getName(tagClass: LuaDocTagClass): String {
    val stub = tagClass.stub
    if (stub != null)
        return stub.className
    return tagClass.id.text
}

fun getName(tagAlias: LuaDocTagAlias): String {
    val stub = tagAlias.stub
    if (stub != null)
        return stub.name
    return tagAlias.id.text
}

/**
 * for Goto Class
 * @param tagClass class def
 * *
 * @return ItemPresentation
 */
fun getPresentation(tagClass: LuaDocTagClass): ItemPresentation {
    return object : ItemPresentation {
        override fun getPresentableText(): String? {
            return tagClass.name
        }

        override fun getLocationString(): String? {
            return tagClass.containingFile.name
        }

        override fun getIcon(b: Boolean): Icon? {
            return AllIcons.Nodes.Class
        }
    }
}

fun getType(tagClass: LuaDocTagClass): ITyClass {
    val stub = tagClass.stub
    return stub?.classType ?: TyPsiDocClass(tagClass)
}

fun getType(genericDef: LuaDocGenericDef): TyGenericParameter {
    return TyGenericParameter(genericDef)
}

fun isDeprecated(tagClass: LuaDocTagClass): Boolean {
    val stub = tagClass.stub
    return stub?.isDeprecated ?: LuaCommentUtil.findContainer(tagClass).isDeprecated
}

fun isShape(tagClass: LuaDocTagClass): Boolean {
    val stub = tagClass.stub
    return stub?.isShape ?: tagClass.shape != null
}

fun getType(tagType: LuaDocTagType, index: Int): ITy {
    val tyList = tagType.typeList?.tyList

    if (tyList == null) {
        return Primitives.UNKNOWN
    }

    return tyList.getOrNull(index)?.getType() ?: if (tagType.variadic != null) {
        tyList.last().getType()
    } else {
        Primitives.UNKNOWN
    }
}

fun getType(tagType: LuaDocTagType): ITy {
    val list = tagType.typeList?.tyList?.map { it.getType() }
    return if (list == null) {
        Primitives.UNKNOWN
    } else if (list.size == 1 && tagType.variadic == null) {
        list.first()
    } else {
        TyMultipleResults(list, tagType.variadic != null)
    }
}

fun getType(tagNot: LuaDocTagNot, index: Int): ITy {
    val tyList = tagNot.typeList?.tyList

    if (tyList == null) {
        return Primitives.VOID
    }

    return tyList.getOrNull(index)?.getType() ?: if (tagNot.variadic != null) {
        tyList.last().getType()
    } else {
        Primitives.VOID
    }
}

fun getType(tagNot: LuaDocTagNot): ITy {
    val list = tagNot.typeList?.tyList?.map { it.getType() }
    return if (list != null && list.size > 0) {
        if (list.size > 1 || tagNot.variadic != null) {
            TyMultipleResults(list, tagNot.variadic != null)
        } else {
            list.first()
        }
    } else {
        Primitives.VOID
    }
}

@Suppress("UNUSED_PARAMETER")
fun toString(stubElement: StubBasedPsiElement<out StubElement<*>>): String {
    return "[STUB]"// + stubElement.getNode().getElementType().toString();
}

fun getName(tagField: LuaDocTagField): String? {
    val stub = tagField.stub
    if (stub != null)
        return stub.name
    return getName(tagField as PsiNameIdentifierOwner)
}

fun getPresentation(tagField: LuaDocTagField): ItemPresentation {
    return object : ItemPresentation {
        override fun getPresentableText(): String? {
            return tagField.name
        }

        override fun getLocationString(): String? {
            return tagField.containingFile.name
        }

        override fun getIcon(b: Boolean): Icon? {
            return AllIcons.Nodes.Field
        }
    }
}

fun isExplicitlyTyped(tagField: LuaDocTagField): Boolean {
    return true
}

fun getType(luaDocGeneralTy: LuaDocGeneralTy): ITy {
    val typeRef = luaDocGeneralTy.typeRef

    if (typeRef.id == null) {
        return Primitives.TABLE
    }

    return withRecursionGuard("getType", luaDocGeneralTy) {
        SearchContext.withDumb(luaDocGeneralTy.project, null) {
            resolveType(typeRef, it)
        }
    } ?: TyLazyClass(typeRef.name, luaDocGeneralTy)
}

fun getType(luaDocFunctionTy: LuaDocFunctionTy): ITy {
    return TyDocPsiFunction(luaDocFunctionTy)
}

fun getParams(luaDocFunctionTy: LuaDocFunctionTy): Array<LuaParamInfo>? {
    return luaDocFunctionTy.functionParams?.let  {
        it.functionParamList.map {
            LuaParamInfo(it.id.text, it.ty?.getType())
        }.toTypedArray()
    }
}

fun getVarargParam(luaDocFunctionTy: LuaDocFunctionTy): ITy? {
    return luaDocFunctionTy.functionParams?.varargParam?.type
}

fun getReturnType(luaDocFunctionTy: LuaDocFunctionTy): ITy? {
    return luaDocFunctionTy.functionReturnType?.let { getReturnType(it) }
}

fun getType(luaDocGenericTy: LuaDocGenericTy): ITy {
    val paramTys = luaDocGenericTy.tyList.map { it.getType() }.toTypedArray()
    val baseTy = withRecursionGuard("getType", luaDocGenericTy) {
        SearchContext.withDumb(luaDocGenericTy.project, null) {
            luaDocGenericTy.typeRef.resolveType(it)
        }
    } ?: TyLazyClass(luaDocGenericTy.typeRef.name, luaDocGenericTy)
    return TyGeneric(paramTys, baseTy)
}

fun getType(luaDocParTy: LuaDocParTy): ITy {
    return luaDocParTy.ty.getType()
}

fun getType(booleanLiteral: LuaDocBooleanLiteralTy): ITy {
    return TyPrimitiveLiteral.getTy(TyPrimitiveKind.Boolean, booleanLiteral.value.text)
}

fun getType(numberLiteral: LuaDocNumberLiteralTy): ITy {
    val n = LuaNumber.getValue(numberLiteral.number.text)
    val valueString = if (numberLiteral.negative != null) "-${n}" else n.toString()
    return if (n != null) {
        TyPrimitiveLiteral.getTy(TyPrimitiveKind.Number, valueString)
    } else Primitives.UNKNOWN
}

fun getType(stringLiteral: LuaDocStringLiteralTy): ITy {
    return TyPrimitiveLiteral.getTy(TyPrimitiveKind.String, LuaString.getContent(stringLiteral.value.text).value)
}

fun getType(snippet: LuaDocSnippetTy): ITy {
    return TySnippet(snippet.content.text)
}

fun getType(unionTy: LuaDocUnionTy): ITy {
    return unionTy.tyList.fold<LuaDocTy, ITy?>(null) { ty, docTy ->
        SearchContext.withDumb(unionTy.project, null) { context ->
            TyUnion.union(ty, docTy.getType(), context)
        }
    } ?: Primitives.UNKNOWN
}

fun getReference(see: LuaDocTagSee): PsiReference? {
    if (see.id == null) return null
    return LuaDocSeeReference(see)
}

fun getType(tbl: LuaDocTableTy): ITy {
    return TyDocTable(tbl.tableDef)
}

fun guessParentType(f: LuaDocTableField, context: SearchContext): ITy {
    val p = f.parent as LuaDocTableDef
    return TyDocTable(p)
}

fun getVisibility(f: LuaDocTableField): Visibility {
    return Visibility.PUBLIC
}

fun getNameIdentifier(f: LuaDocTableField): PsiElement? {
    return f.id
}

fun getName(f:LuaDocTableField): String? {
    val stub = f.stub
    return if (stub != null) {
        stub.name
    } else {
        getName(f as PsiNameIdentifierOwner)
    }
}

fun getIndexType(f: LuaDocTableField): LuaDocTy? {
    val isIndexExpression = f.stub?.let {
        it.name == null
    } ?: f.lbrack != null

    return if (isIndexExpression) {
        PsiTreeUtil.getStubChildOfType(f, LuaDocTy::class.java)
    } else null
}

fun getValueType(f: LuaDocTableField): LuaDocTy? {
    val isIndexExpression = f.stub?.let {
        it.name == null
    } ?: f.lbrack != null

    return if (isIndexExpression) {
        PsiTreeUtil.getStubChildrenOfTypeAsList(f, LuaDocTy::class.java).getOrNull(1)
    } else PsiTreeUtil.getStubChildOfType(f, LuaDocTy::class.java)
}

fun guessIndexType(f: LuaDocTableField, context: SearchContext): ITy? {
    if (f.name != null) {
        return null
    }

    val indexTy = f.stub?.indexTy ?: f.lbrack?.let {
        f.indexType?.getType() ?: Primitives.UNKNOWN
    }

    if (indexTy != null) {
        return indexTy
    } else {
        var fieldIndex = 0
        val siblingFields = f.parent.children

        for (i in 0 until siblingFields.size) {
            val siblingField = siblingFields[i]

            if (siblingField is LuaDocTableField && siblingField.lbrack == null && siblingField.name == null) {
                fieldIndex += 1
            }

            if (siblingField == f) {
                break
            }
        }

        return TyPrimitiveLiteral.getTy(TyPrimitiveKind.Number, fieldIndex.toString())
    }
}

fun guessType(f: LuaDocTableField, context: SearchContext): ITy {
    f.stub?.valueTy?.let {
        return it
    }

    return f.valueType?.getType() ?: Primitives.UNKNOWN
}

fun isExplicitlyTyped(f: LuaDocTableField): Boolean {
    return true
}

fun getNameIdentifier(g: LuaDocGenericDef): PsiElement? {
    return g.id
}

fun isDeprecated(member: LuaPsiTypeMember): Boolean {
    return false
}

fun getNameIdentifier(g: LuaDocTagAlias): PsiElement {
    return g.id
}

fun getType(alias: LuaDocTagAlias): TyAlias {
    val stub = alias.stub
    return if (stub != null) {
        return stub.type
    } else {
        TyAlias(alias.name, alias.genericDefList.map { TyGenericParameter(it) }.toTypedArray(), alias.ty?.getType() ?: Primitives.UNKNOWN)
    }
}

fun getName(luaDocPrimitiveTableTy: LuaDocPrimitiveTableTy): String? {
    return null
}

fun getType(luaDocPrimitiveTableTy: LuaDocPrimitiveTableTy): ITy {
    return TyDocPrimitiveTable(luaDocPrimitiveTableTy)
}

fun getVisibility(luaDocPrimitiveTableTy: LuaDocPrimitiveTableTy): Visibility {
    return Visibility.PUBLIC
}

// WARNING: LuaClassMember requires us to implement guessType() returning the *member* value type.
fun guessType(luaDocPrimitiveTableTy: LuaDocPrimitiveTableTy, context: SearchContext): ITy {
    return Primitives.UNKNOWN
}

fun guessParentType(luaDocPrimitiveTableTy: LuaDocPrimitiveTableTy, context: SearchContext): ITy {
    return luaDocPrimitiveTableTy.getType()
}

fun isExplicitlyTyped(luaDocPrimitiveTableTy: LuaDocPrimitiveTableTy): Boolean {
    return true
}

fun getName(luaDocGenericTableTy: LuaDocGenericTableTy): String? {
    return null
}

fun getType(luaDocGenericTableTy: LuaDocGenericTableTy): ITy {
    return TyDocTableGeneric(luaDocGenericTableTy)
}

fun getVisibility(luaDocGenericTableTy: LuaDocGenericTableTy): Visibility {
    return Visibility.PUBLIC
}

// WARNING: LuaClassMember requires us to implement guessType() returning the *member* value type.
fun guessType(luaDocGenericTableTy: LuaDocGenericTableTy, context: SearchContext): ITy {
    return luaDocGenericTableTy.valueType?.getType() ?: Primitives.UNKNOWN
}

fun guessParentType(luaDocGenericTableTy: LuaDocGenericTableTy, context: SearchContext): ITy {
    return luaDocGenericTableTy.getType()
}

fun isExplicitlyTyped(luaDocGenericTableTy: LuaDocGenericTableTy): Boolean {
    return true
}

fun getName(luaDocArrTy: LuaDocArrTy): String? {
    return null
}

fun getType(luaDocArrTy: LuaDocArrTy): ITy {
    return TyDocArray(luaDocArrTy)
}

fun getVisibility(luaDocArrTy: LuaDocArrTy): Visibility {
    return Visibility.PUBLIC
}

fun guessIndexType(luaDocArrTy: LuaDocArrTy, context: SearchContext): ITy {
    return Primitives.NUMBER
}

// WARNING: LuaClassMember requires us to implement guessType() returning the *member* value type.
fun guessType(luaDocArrTy: LuaDocArrTy, context: SearchContext): ITy {
    return luaDocArrTy.ty.getType()
}

fun guessParentType(luaDocArrTy: LuaDocArrTy, context: SearchContext): ITy {
    return luaDocArrTy.getType()
}

fun isExplicitlyTyped(luaDocArrTy: LuaDocArrTy): Boolean {
    return true
}
