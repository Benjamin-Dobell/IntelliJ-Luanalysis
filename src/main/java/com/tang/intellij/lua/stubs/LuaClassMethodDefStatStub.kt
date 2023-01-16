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
import com.intellij.psi.stubs.*
import com.intellij.util.BitUtil
import com.intellij.util.io.StringRef
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.psi.impl.LuaClassMethodDefStatImpl
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassMemberIndex
import com.tang.intellij.lua.stubs.index.StubKeys
import com.tang.intellij.lua.ty.*

/**
 * class method static/instance
 * Created by tangzx on 2016/12/4.
 */
class LuaClassMethodType : LuaStubElementType<LuaClassMethodDefStatStub, LuaClassMethodDefStat>("Class Method") {

    override fun createPsi(luaClassMethodDefStatStub: LuaClassMethodDefStatStub): LuaClassMethodDefStat {
        return LuaClassMethodDefStatImpl(luaClassMethodDefStatStub, this)
    }

    override fun createStub(def: LuaClassMethodDefStat, stubElement: StubElement<*>): LuaClassMethodDefStatStub {
        val methodName = def.classMethodName
        val id = def.nameIdentifier
        val expr = methodName.expression
        val classNameSet = mutableListOf<ITyClass>()

        SearchContext.withDumb(def.project, null) {
            SearchContext.infer(it, expr)
        }?.let {
            TyUnion.each(it) {
                if (it is ITyClass)
                    classNameSet.add(it)
            }
        }

        val visibility = def.visibility
        val isStatic = methodName.dot != null
        val isDeprecated = def.isDeprecated
        val isExplicitlyTyped = def.isExplicitlyTyped

        var flags = 0
        flags = BitUtil.set(flags, visibility.bitMask, true)
        flags = BitUtil.set(flags, FLAG_STATIC, isStatic)
        flags = BitUtil.set(flags, FLAG_DEPRECATED, isDeprecated)
        flags = BitUtil.set(flags, FLAG_EXPLICITLY_TYPED, isExplicitlyTyped)

        val retDocTy = def.comment?.tagReturn?.type
        val params = def.params
        val overloads = def.overloads
        val genericParams = def.genericParams

        return LuaClassMethodDefStatStubImpl(flags,
                id?.text ?: "",
                classNameSet.toTypedArray(),
                retDocTy,
                params,
                genericParams,
                overloads,
                def.varargType,
                stubElement)
    }

    override fun shouldCreateStub(node: ASTNode): Boolean {
        //确定是完整的，并且是 class:method, class.method 形式的， 否则会报错
        val psi = node.psi as LuaClassMethodDefStat
        return psi.funcBody != null
    }

    private fun StubOutputStream.writeTypes(types: Array<ITyClass>) {
        writeByte(types.size)
        types.forEach { Ty.serialize(it, this) }
    }

    override fun serialize(stub: LuaClassMethodDefStatStub, stubOutputStream: StubOutputStream) {
        stubOutputStream.writeTypes(stub.classes)
        stubOutputStream.writeName(stub.name)
        stubOutputStream.writeShort(stub.flags)
        stubOutputStream.writeTyNullable(stub.returnDocTy)
        stubOutputStream.writeParamInfoArray(stub.params)
        stubOutputStream.writeGenericParamsNullable(stub.genericParams)
        stubOutputStream.writeTyNullable(stub.varargTy)
        stubOutputStream.writeSignatures(stub.overloads)
    }

    private fun StubInputStream.readTypes(): Array<ITyClass> {
        val size = readByte()
        val list = mutableListOf<ITyClass>()
        for (i in 0 until size) {
            val ty = Ty.deserialize(this) as? ITyClass ?: continue
            list.add(ty)
        }
        return list.toTypedArray()
    }

    override fun deserialize(stubInputStream: StubInputStream, stubElement: StubElement<*>): LuaClassMethodDefStatStub {
        val classes = stubInputStream.readTypes()
        val shortName = stubInputStream.readName()
        val flags = stubInputStream.readShort()
        val retDocTy = stubInputStream.readTyNullable()
        val params = stubInputStream.readParamInfoArray()
        val genericParams = stubInputStream.readGenericParamsNullable()
        val varargTy = stubInputStream.readTyNullable()
        val overloads = stubInputStream.readSignatures()

        return LuaClassMethodDefStatStubImpl(flags.toInt(),
                StringRef.toString(shortName),
                classes,
                retDocTy,
                params,
                genericParams,
                overloads,
                varargTy,
                stubElement)
    }

    override fun indexStub(luaClassMethodDefStatStub: LuaClassMethodDefStatStub, indexSink: IndexSink) {
        val classNames = luaClassMethodDefStatStub.classes
        val shortName = luaClassMethodDefStatStub.name
        classNames.forEach {
            LuaClassMemberIndex.indexMemberStub(indexSink, it.className, shortName)
        }
        indexSink.occurrence(StubKeys.SHORT_NAME, shortName)
    }

    companion object {
        const val FLAG_STATIC = 0x10
        const val FLAG_DEPRECATED = 0x20
        const val FLAG_EXPLICITLY_TYPED = 0x40
    }
}

interface LuaClassMethodDefStatStub : LuaFuncBodyOwnerStub<LuaClassMethodDefStat>, LuaClassMemberStub<LuaClassMethodDefStat> {

    val classes: Array<ITyClass>

    val name: String

    val isStatic: Boolean

    val flags: Int
}

class LuaClassMethodDefStatStubImpl(
    override val flags: Int,
    override val name: String,
    override val classes: Array<ITyClass>,
    override val returnDocTy: ITy?,
    override val params: Array<LuaParamInfo>,
    override val genericParams: Array<TyGenericParameter>?,
    override val overloads: Array<IFunSignature>,
    override val varargTy: ITy?,
    parent: StubElement<*>
) : StubBase<LuaClassMethodDefStat>(parent, LuaElementType.CLASS_METHOD_DEF_STAT), LuaClassMethodDefStatStub {
    override val docTy: ITy? = null

    override val isStatic: Boolean
        get() = BitUtil.isSet(flags, LuaClassMethodType.FLAG_STATIC)

    override val isDeprecated: Boolean
        get() = BitUtil.isSet(flags, LuaClassMethodType.FLAG_DEPRECATED)

    override val isExplicitlyTyped: Boolean
        get() = BitUtil.isSet(flags, LuaClassMethodType.FLAG_EXPLICITLY_TYPED)

    override val visibility: Visibility
        get() = Visibility.getWithMask(flags)
}
