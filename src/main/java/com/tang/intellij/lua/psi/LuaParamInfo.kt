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

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.io.StringRef
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.readTyNullable
import com.tang.intellij.lua.stubs.writeTyNullable
import com.tang.intellij.lua.ty.ITy
import com.tang.intellij.lua.ty.ITySubstitutor
import com.tang.intellij.lua.ty.Primitives
import com.tang.intellij.lua.ty.TyMultipleResults

/**
 * parameter info
 * Created by tangzx on 2017/2/4.
 */
class LuaParamInfo(val name: String, val ty: ITy?, val optional: Boolean) {

    override fun equals(other: Any?): Boolean {
        return other is LuaParamInfo && other.optional == optional && other.ty == ty
    }

    fun equals(context: SearchContext, other: LuaParamInfo, equalityFlags: Int): Boolean {
        if (ty == null) {
            return other.ty == null
        } else if (other.ty == null) {
            return false
        } else if (other.optional != optional) {
            return false
        }

        return ty.equals(context, other.ty, equalityFlags)
    }

    override fun hashCode(): Int {
        return if (optional) {
            31 * ty.hashCode()
        } else {
            ty.hashCode()
        }
    }

    fun substitute(context: SearchContext, substitutor: ITySubstitutor): LuaParamInfo {
        val ty = this.ty ?: Primitives.UNKNOWN
        val substitutedTy = TyMultipleResults.getResult(context, ty.substitute(context, substitutor))

        if (substitutedTy === ty) {
            return this
        }

        return LuaParamInfo(name, substitutedTy, optional)
    }

    companion object {
        fun createSelf(thisType: ITy? = null): LuaParamInfo {
            return LuaParamInfo(Constants.WORD_SELF, thisType, false)
        }

        fun deserialize(stubInputStream: StubInputStream): LuaParamInfo {
            val name = StringRef.toString(stubInputStream.readName())
            val ty = stubInputStream.readTyNullable()
            val optional = stubInputStream.readBoolean()
            return LuaParamInfo(name, ty, optional)
        }

        fun serialize(param: LuaParamInfo, stubOutputStream: StubOutputStream) {
            stubOutputStream.writeName(param.name)
            stubOutputStream.writeTyNullable(param.ty)
            stubOutputStream.writeBoolean(param.optional)
        }
    }
}
