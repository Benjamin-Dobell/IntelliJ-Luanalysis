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
import com.tang.intellij.lua.psi.LuaParamDef
import com.tang.intellij.lua.psi.impl.LuaParamDefImpl
import com.tang.intellij.lua.ty.ITy
import com.tang.intellij.lua.ty.getAnonymousTypeName

class LuaParamDefElementType : LuaStubElementType<LuaParamDefStub, LuaParamDef>("PARAM_DEF") {
    override fun indexStub(stub: LuaParamDefStub, sink: IndexSink) {

    }

    override fun createStub(paramDef: LuaParamDef, parentStub: StubElement<*>?): LuaParamDefStub {
        val name = paramDef.name
        val anonymous = getAnonymousTypeName(paramDef)
        val commentOwner = PsiTreeUtil.getParentOfType(paramDef, LuaCommentOwner::class.java)
        val comment = commentOwner?.comment
        val docTy = comment?.getParamDef(name)?.type ?: comment?.tagClass?.type

        return LuaParamDefStub(name, anonymous, docTy, parentStub, LuaElementType.PARAM_DEF)
    }

    override fun serialize(stub: LuaParamDefStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.name)
        dataStream.writeName(stub.anonymousType)
        dataStream.writeTyNullable(stub.docTy)
    }

    override fun createPsi(stub: LuaParamDefStub): LuaParamDef {
        return LuaParamDefImpl(stub, LuaElementType.PARAM_DEF)
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): LuaParamDefStub {
        val name = dataStream.readName()
        val anonymous = dataStream.readName()
        val docTy = dataStream.readTyNullable()
        return LuaParamDefStub(StringRef.toString(name),
                StringRef.toString(anonymous),
                docTy,
                parentStub,
                LuaElementType.PARAM_DEF)
    }
}

class LuaParamDefStub(
        val name: String,
        val anonymousType:String,
        override val docTy: ITy?,
        parentStub: StubElement<*>?,
        type: LuaStubElementType<*, *>
) : LuaStubBase<LuaParamDef>(parentStub, type), LuaDocTyStub
