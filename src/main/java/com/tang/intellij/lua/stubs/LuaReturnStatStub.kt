/*
 * Copyright (c) 2020
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

import com.intellij.psi.stubs.*
import com.tang.intellij.lua.psi.LuaReturnStat
import com.tang.intellij.lua.psi.LuaTypes
import com.tang.intellij.lua.psi.impl.LuaReturnStatImpl
import com.tang.intellij.lua.stubs.LuaDocTyStub
import com.tang.intellij.lua.stubs.LuaStubElementType
import com.tang.intellij.lua.stubs.readTyNullable
import com.tang.intellij.lua.stubs.writeTyNullable
import com.tang.intellij.lua.ty.ITy

class LuaReturnStatType : LuaStubElementType<LuaReturnStatStub, LuaReturnStat>("Return Statement") {
    override fun createPsi(stub: LuaReturnStatStub): LuaReturnStat {
        return LuaReturnStatImpl(stub, this)
    }

    override fun createStub(psi: LuaReturnStat, parentStub: StubElement<*>): LuaReturnStatStub {
        return LuaReturnStatStubImpl(psi.getType(), parentStub)
    }

    override fun serialize(stub: LuaReturnStatStub, stream: StubOutputStream) {
        stream.writeTyNullable(stub.docTy)
    }

    override fun deserialize(stream: StubInputStream, parentStub: StubElement<*>): LuaReturnStatStub {
        val docTy = stream.readTyNullable()
        return LuaReturnStatStubImpl(docTy, parentStub)
    }

    override fun indexStub(stub: LuaReturnStatStub, sink: IndexSink) = Unit
}

interface LuaReturnStatStub : StubElement<LuaReturnStat>, LuaDocTyStub

class LuaReturnStatStubImpl(
    override val docTy: ITy?,
    parent: StubElement<*>
) : StubBase<LuaReturnStat>(parent, LuaTypes.RETURN_STAT as IStubElementType<*, *>), LuaReturnStatStub
