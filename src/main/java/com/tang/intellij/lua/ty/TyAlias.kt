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

package com.tang.intellij.lua.ty

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.io.StringRef
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.readGenericParamsNullable
import com.tang.intellij.lua.stubs.writeGenericParamsNullable

interface ITyAlias : ITyResolvable {
    val name: String
    val params: Array<TyGenericParameter>?
    val ty: ITy

    override fun willResolve(context: SearchContext): Boolean {
        return true
    }

    override fun resolve(context: SearchContext, genericArgs: Array<out ITy>?): ITy {
        val params = this.params

        return if (params != null && genericArgs != null) {
            val paramSubstitutor = TyGenericParameterSubstitutor.withArgs(params, genericArgs)
            ty.substitute(context, paramSubstitutor)
        } else {
            ty
        }
    }
}

class TyAlias(override val name: String,
              override val params: Array<TyGenericParameter>?,
              override val ty: ITy
) : Ty(TyKind.Alias), ITyAlias {

    override fun equals(other: Any?): Boolean {
        return other is ITyAlias && other.name == name && other.flags == flags
    }

    override fun equals(context: SearchContext, other: ITy, equalityFlags: Int): Boolean {
        if (this === other) {
            return true
        }

        return ty.equals(context, other, equalityFlags)
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun processMembers(context: SearchContext, deep: Boolean, process: ProcessTypeMember): Boolean {
        return ty.processMembers(context, deep, process)
    }

    override fun processMember(context: SearchContext, name: String, deep: Boolean, indexerSubstitutor: ITySubstitutor?, process: ProcessTypeMember): Boolean {
        return ty.processMember(context, name, deep, indexerSubstitutor, process)
    }

    override fun processIndexer(context: SearchContext, indexTy: ITy, exact: Boolean, deep: Boolean, indexerSubstitutor: ITySubstitutor?, process: ProcessTypeMember): Boolean {
        return ty.processIndexer(context, indexTy, exact, deep, indexerSubstitutor, process)
    }

    override fun accept(visitor: ITyVisitor) {
        visitor.visitAlias(this)
    }

    override fun getParams(context: SearchContext): Array<TyGenericParameter>? {
        return params
    }

    override fun substitute(context: SearchContext, substitutor: ITySubstitutor): ITy {
        return substitutor.substitute(context, this)
    }

    override fun contravariantOf(context: SearchContext, other: ITy, varianceFlags: Int): Boolean {
        return ty.contravariantOf(context, other, varianceFlags) || super.contravariantOf(context, other, varianceFlags)
    }
}

object TyAliasSerializer : TySerializer<ITyAlias>() {
    override fun deserializeTy(flags: Int, stream: StubInputStream): ITyAlias {
        val name = stream.readName()
        val params = stream.readGenericParamsNullable()
        val ty = Ty.deserialize(stream)
        return TyAlias(StringRef.toString(name), params, ty)
    }

    override fun serializeTy(ty: ITyAlias, stream: StubOutputStream) {
        stream.writeName(ty.name)
        stream.writeGenericParamsNullable(ty.params)
        Ty.serialize(ty.ty, stream)
    }
}
