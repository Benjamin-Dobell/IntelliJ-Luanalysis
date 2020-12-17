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
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.psi.impl.LuaFuncDefStatImpl
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassMemberIndex
import com.tang.intellij.lua.stubs.index.StubKeys
import com.tang.intellij.lua.ty.IFunSignature
import com.tang.intellij.lua.ty.ITy
import com.tang.intellij.lua.ty.TyGenericParameter

/**

 * Created by tangzx on 2016/11/26.
 */
class LuaFuncType : LuaStubElementType<LuaFuncDefStatStub, LuaFuncDefStat>("Global Function") {

    override fun createPsi(luaGlobalFuncStub: LuaFuncDefStatStub): LuaFuncDefStat {
        return LuaFuncDefStatImpl(luaGlobalFuncStub, this)
    }

    override fun createStub(funcDefStat: LuaFuncDefStat, stubElement: StubElement<*>): LuaFuncDefStatStub {
        val nameRef = funcDefStat.nameIdentifier!!
        val file = funcDefStat.containingFile
        val moduleName = (file as? LuaPsiFile)?.let {
            SearchContext.withDumb(funcDefStat.project, null) {
                file.getModuleName(it)
            }
        } ?: Constants.WORD_G
        val retDocTy = funcDefStat.comment?.tagReturn?.type
        val params = funcDefStat.params
        val genericParams = funcDefStat.genericParams
        val overloads = funcDefStat.overloads

        var flags = BitUtil.set(0, funcDefStat.visibility.bitMask, true)
        flags = BitUtil.set(flags, FLAG_DEPRECATED, funcDefStat.isDeprecated)

        return LuaFuncDefStatStubImpl(nameRef.text,
                moduleName,
                flags,
                retDocTy,
                funcDefStat.varargType,
                params,
                genericParams,
                overloads,
                stubElement)
    }

    override fun shouldCreateStub(node: ASTNode): Boolean {
        val element = node.psi as LuaFuncDefStat
        return element.funcBody != null && element.nameIdentifier != null
    }

    override fun serialize(stub: LuaFuncDefStatStub, stream: StubOutputStream) {
        stream.writeName(stub.name)
        stream.writeName(stub.module)
        stream.writeShort(stub.flags)
        stream.writeTyNullable(stub.returnDocTy)
        stream.writeTyNullable(stub.varargTy)
        stream.writeParamInfoArray(stub.params)
        stream.writeGenericParamsNullable(stub.genericParams)
        stream.writeSignatures(stub.overloads)
    }

    override fun deserialize(stream: StubInputStream, stubElement: StubElement<*>): LuaFuncDefStatStub {
        val name = stream.readName()
        val module = stream.readName()
        val flags = stream.readShort()
        val retDocTy = stream.readTyNullable()
        val varargTy = stream.readTyNullable()
        val params = stream.readParamInfoArray()
        val genericParams = stream.readGenericParamsNullable()
        val overloads = stream.readSignatures()
        return LuaFuncDefStatStubImpl(StringRef.toString(name),
                StringRef.toString(module),
                flags.toInt(),
                retDocTy,
                varargTy,
                params,
                genericParams,
                overloads,
                stubElement)
    }

    override fun indexStub(luaGlobalFuncStub: LuaFuncDefStatStub, indexSink: IndexSink) {
        val name = luaGlobalFuncStub.name
        val moduleName = luaGlobalFuncStub.module

        LuaClassMemberIndex.indexMemberStub(indexSink, moduleName, name)

        indexSink.occurrence(StubKeys.SHORT_NAME, name)
    }

    companion object {
        const val FLAG_DEPRECATED = 0x20
    }
}

interface LuaFuncDefStatStub : LuaFuncBodyOwnerStub<LuaFuncDefStat>, LuaClassMemberStub<LuaFuncDefStat> {
    val name: String
    val module: String
    val flags: Int
}

class LuaFuncDefStatStubImpl(
    override val name: String,
    override val module: String,
    override val flags: Int,
    override val returnDocTy: ITy?,
    override val varargTy: ITy?,
    override val params: Array<LuaParamInfo>,
    override val genericParams: Array<TyGenericParameter>?,
    override val overloads: Array<IFunSignature>,
    parent: StubElement<*>
) : StubBase<LuaFuncDefStat>(parent, LuaTypes.FUNC_DEF_STAT as IStubElementType<*, *>), LuaFuncDefStatStub {
    override val docTy: ITy?
        get() = null

    override val isDeprecated: Boolean
        get() = BitUtil.isSet(flags, LuaFuncType.FLAG_DEPRECATED)

    override val visibility: Visibility
        get() = Visibility.getWithMask(flags)
}
