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

package com.tang.intellij.lua.stubs

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.BitUtil
import com.intellij.util.io.StringRef
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.psi.impl.LuaTableFieldImpl
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassMemberIndex
import com.tang.intellij.lua.stubs.index.StubKeys
import com.tang.intellij.lua.ty.*

class LuaTableFieldType : LuaStubElementType<LuaTableFieldStub, LuaTableField>("TABLE_FIELD") {

    override fun createPsi(luaTableFieldStub: LuaTableFieldStub): LuaTableField {
        return LuaTableFieldImpl(luaTableFieldStub, this)
    }

    override fun shouldCreateStub(node: ASTNode): Boolean {
        val tableField = node.psi as LuaTableField
        return tableField.shouldCreateStub
    }

    private fun findTableExprTypeName(field: LuaTableField): String? {
        val table = PsiTreeUtil.getParentOfType(field, LuaTableExpr::class.java)
        return if (table != null) getTableTypeName(table) else null
    }

    override fun createStub(field: LuaTableField, parentStub: StubElement<*>): LuaTableFieldStub {
        val className = findTableExprTypeName(field)
        val indexTy = field.idExpr?.let {
            SearchContext.withDumb(it.project, Ty.UNKNOWN) { context ->
                it.guessType(context)
            }
        }

        val flags = BitUtil.set(0, FLAG_DEPRECATED, field.isDeprecated)
        var valueTy = field.comment?.docTy ?: (field.valueExpr as? LuaLiteralExpr)?.infer()

        if (field.name != null) {
            valueTy = if (valueTy is TyMultipleResults) valueTy.list.first() else valueTy

            return LuaTableFieldStubImpl(
                    parentStub,
                    this,
                    className,
                    field.name,
                    flags,
                    valueTy)
        } else if (indexTy != null) {
            valueTy = if (valueTy is TyMultipleResults) valueTy.list.first() else valueTy

            return LuaTableFieldStubImpl(
                    parentStub,
                    this,
                    className,
                    indexTy,
                    true,
                    flags,
                    valueTy)
        } else {
            var fieldIndex = 0
            val siblingFields = field.parent.children

            for (i in 0 until siblingFields.size) {
                val siblingField = siblingFields[i]

                if (siblingField is LuaTableField && field.idExpr == null && field.name == null) {
                    fieldIndex += 1
                }

                if (siblingField == field) {
                    break
                }
            }

            valueTy = if (valueTy is TyMultipleResults) {
                if (fieldIndex + 1 == siblingFields.size) valueTy else valueTy.list.first()
            } else valueTy

            return LuaTableFieldStubImpl(
                    parentStub,
                    this,
                    className,
                    TyPrimitiveLiteral.getTy(TyPrimitiveKind.Number, fieldIndex.toString()),
                    false,
                    flags,
                    valueTy)
        }
    }

    override fun serialize(stub: LuaTableFieldStub, stubOutputStream: StubOutputStream) {
        stubOutputStream.writeName(stub.className)
        stubOutputStream.writeName(stub.name)
        stubOutputStream.writeTyNullable(stub.indexTy)

        if (stub.indexTy != null) {
            stubOutputStream.writeBoolean(stub.isIndexExpression)
        }

        stubOutputStream.writeShort(stub.flags)
        stubOutputStream.writeTyNullable(stub.valueTy)
    }

    override fun deserialize(stubInputStream: StubInputStream, stubElement: StubElement<*>): LuaTableFieldStub {
        val className = StringRef.toString(stubInputStream.readName())
        val name = StringRef.toString(stubInputStream.readName())
        val indexType = stubInputStream.readTyNullable()
        val isIndexExpression = if (indexType != null) {
            stubInputStream.readBoolean()
        } else false
        val flags = stubInputStream.readShort().toInt()
        val valueType = stubInputStream.readTyNullable()

        return if (name != null) {
            LuaTableFieldStubImpl(stubElement,
                    this,
                    className,
                    name,
                    flags,
                    valueType)
        } else {
            LuaTableFieldStubImpl(stubElement,
                    this,
                    className,
                    indexType!!,
                    isIndexExpression,
                    flags,
                    valueType)
        }
    }

    override fun indexStub(fieldStub: LuaTableFieldStub, indexSink: IndexSink) {
        val className = fieldStub.className ?: return
        val fieldName = fieldStub.name

        if (fieldName != null) {
            LuaClassMemberIndex.indexMemberStub(indexSink, className, fieldName)
            indexSink.occurrence(StubKeys.SHORT_NAME, fieldName)
        }

        val indexTy = fieldStub.indexTy ?: return
        val valueTy = fieldStub.valueTy

        if (valueTy is TyMultipleResults) {
            if (valueTy.variadic) {
                LuaClassMemberIndex.indexIndexerStub(indexSink, className, Ty.NUMBER)
            } else {
                val startIndex = (indexTy as TyPrimitiveLiteral).value.toInt()

                for (i in 0 until valueTy.list.size) {
                    val resultIndexTy = TyPrimitiveLiteral.getTy(TyPrimitiveKind.Number, (startIndex + i).toString())
                    LuaClassMemberIndex.indexIndexerStub(indexSink, className, resultIndexTy)
                }
            }
        } else {
            LuaClassMemberIndex.indexIndexerStub(indexSink, className, indexTy)
        }
    }

    companion object {
        const val FLAG_DEPRECATED = 0x20
    }
}

/**
 * table field stub
 * Created by tangzx on 2017/1/14.
 */
interface LuaTableFieldStub : LuaClassMemberStub<LuaTableField> {
    val className: String?

    val name: String?
    val indexTy: ITy?
    val isIndexExpression: Boolean

    val flags: Int

    val valueTy: ITy?

    override val docTy: ITy?
        get() = valueTy
}

class LuaTableFieldStubImpl : LuaStubBase<LuaTableField>, LuaTableFieldStub {
    override val className: String?
    override val name: String?
    override val indexTy: ITy?
    override val isIndexExpression: Boolean
    override val flags: Int
    override val valueTy: ITy?

    override val isDeprecated: Boolean
        get() = BitUtil.isSet(flags, LuaDocTagFieldType.FLAG_DEPRECATED)

    override val visibility: Visibility = Visibility.PUBLIC

    constructor(parent: StubElement<*>, elementType: LuaStubElementType<*, *>, className: String?, name: String?, flags: Int, valueTy: ITy?)
            : super(parent, elementType) {
        this.className = className
        this.name = name
        this.indexTy = null
        this.isIndexExpression = false
        this.flags = flags
        this.valueTy = valueTy
    }

    constructor(parent: StubElement<*>, elementType: LuaStubElementType<*, *>, className: String?, indexType: ITy, isIndexExpression: Boolean, flags: Int, valueTy: ITy?)
            : super(parent, elementType) {
        this.className = className
        this.name = null
        this.indexTy = indexType
        this.isIndexExpression = isIndexExpression
        this.flags = flags
        this.valueTy = valueTy
    }
}
