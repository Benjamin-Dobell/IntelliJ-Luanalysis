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

import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.io.StringRef
import com.tang.intellij.lua.psi.LuaCommentOwner
import com.tang.intellij.lua.psi.LuaElementType
import com.tang.intellij.lua.psi.LuaLocalDef
import com.tang.intellij.lua.psi.impl.LuaLocalDefImpl
import com.tang.intellij.lua.ty.ITy
import com.tang.intellij.lua.ty.getAnonymousTypeName

class LuaLocalDefElementType : LuaStubElementType<LuaLocalDefStub, LuaLocalDef>("LOCAL_DEF") {
    override fun indexStub(stub: LuaLocalDefStub, sink: IndexSink) {

    }

    override fun createStub(localDefStat: LuaLocalDef, parentStub: StubElement<*>?): LuaLocalDefStub {
        val name = localDefStat.name
        val anonymous = getAnonymousTypeName(localDefStat)
        val commentOwner = PsiTreeUtil.getParentOfType(localDefStat, LuaCommentOwner::class.java)
        val comment = commentOwner?.comment
        val docTy = comment?.tagType?.getType() ?: comment?.tagClass?.type

        return LuaLocalDefStub(name, anonymous, docTy, parentStub, LuaElementType.LOCAL_DEF)
    }

    override fun serialize(stub: LuaLocalDefStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.name)
        dataStream.writeName(stub.anonymousType)
        dataStream.writeTyNullable(stub.docTy)
    }

    override fun createPsi(stub: LuaLocalDefStub): LuaLocalDef {
        return LuaLocalDefImpl(stub, LuaElementType.LOCAL_DEF)
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): LuaLocalDefStub {
        val name = dataStream.readName()
        val anonymous = dataStream.readName()
        val docTy = dataStream.readTyNullable()
        return LuaLocalDefStub(StringRef.toString(name),
                StringRef.toString(anonymous),
                docTy, parentStub, LuaElementType.LOCAL_DEF)
    }
}

open class LuaLocalDefStub(
        val name: String,
        val anonymousType:String,
        override val docTy: ITy?,
        parentStub: StubElement<*>?,
        type: LuaStubElementType<*, *>
) : LuaStubBase<LuaLocalDef>(parentStub, type), LuaDocTyStub
