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
import com.tang.intellij.lua.ty.*

/**
 * parameter info
 * Created by tangzx on 2017/2/4.
 */
class LuaParamInfo(val name: String, val ty: ITy?) {

    override fun equals(other: Any?): Boolean {
        //only check ty
        return other is LuaParamInfo && other.ty == ty
    }

    fun equals(other: LuaParamInfo, context: SearchContext): Boolean {
        if (ty == null) {
            return other.ty == null
        } else if (other.ty == null) {
            return false
        }

        return ty.equals(other.ty, context)
    }

    override fun hashCode(): Int {
        return ty.hashCode()
    }

    fun substitute(substitutor: ITySubstitutor): LuaParamInfo {
        val ty = this.ty ?: Primitives.UNKNOWN
        val substitutedTy = TyMultipleResults.getResult(substitutor.searchContext, ty.substitute(substitutor))

        if (substitutedTy === ty) {
            return this
        }

        return LuaParamInfo(name, substitutedTy)
    }

    companion object {
        fun createSelf(thisType: ITy? = null): LuaParamInfo {
            return LuaParamInfo(Constants.WORD_SELF, thisType)
        }

        fun deserialize(stubInputStream: StubInputStream): LuaParamInfo {
            val name = StringRef.toString(stubInputStream.readName())
            val ty = stubInputStream.readTyNullable()
            return LuaParamInfo(name, ty)
        }

        fun serialize(param: LuaParamInfo, stubOutputStream: StubOutputStream) {
            stubOutputStream.writeName(param.name)
            stubOutputStream.writeTyNullable(param.ty)
        }
    }
}
