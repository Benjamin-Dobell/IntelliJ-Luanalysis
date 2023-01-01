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

package com.tang.intellij.lua.ty

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.psi.LuaDocPrimitiveTableTy
import com.tang.intellij.lua.search.SearchContext

// All primitive types: boolean, number, function, table, string (as per TyPrimitiveKind)
interface ITyPrimitive : ITy {
    val primitiveKind: TyPrimitiveKind
}

// boolean, number, function, table
class TyPrimitive(override val primitiveKind: TyPrimitiveKind,
                  override val displayName: String) : Ty(TyKind.Primitive), ITyPrimitive {

    override val booleanType = if (primitiveKind == TyPrimitiveKind.Boolean) Primitives.BOOLEAN else Primitives.TRUE

    override fun equals(other: Any?): Boolean {
        return other is ITyPrimitive && other.primitiveKind == primitiveKind
    }

    override fun equals(context: SearchContext, other: ITy): Boolean {
        if (this === other) {
            return true
        }

        val resolvedOther = Ty.resolve(context, other)
        return resolvedOther is ITyPrimitive && resolvedOther.primitiveKind == primitiveKind
    }

    override fun hashCode(): Int {
        return primitiveKind.hashCode()
    }

    override fun contravariantOf(context: SearchContext, other: ITy, varianceFlags: Int): Boolean {
        if (super.contravariantOf(context, other, varianceFlags)
                || (other is ITyPrimitive && other.primitiveKind == primitiveKind)) {
            return true
        }

        if (varianceFlags and TyVarianceFlags.STRICT_UNKNOWN == 0) {
            if (primitiveKind == TyPrimitiveKind.Function && other.kind == TyKind.Function) {
                return true
            }

            if (primitiveKind == TyPrimitiveKind.Table) {
                val otherBase = if (other is ITyGeneric) other.base else other
                return other.kind == TyKind.Array
                        || otherBase.kind == TyKind.Class
                        || otherBase == Primitives.TABLE
            }
        }

        return false
    }

    override fun guessMemberType(searchContext: SearchContext, name: String): ITy? {
        return if (primitiveKind == TyPrimitiveKind.Table) {
            Primitives.UNKNOWN
        } else super<Ty>.guessMemberType(searchContext, name)
    }

    override fun guessIndexerType(context: SearchContext, indexTy: ITy, exact: Boolean): ITy? {
        return if (primitiveKind == TyPrimitiveKind.Table) {
            Primitives.UNKNOWN
        } else super<Ty>.guessIndexerType(context, indexTy, exact)
    }
}

// string
open class TyPrimitiveClass(override val primitiveKind: TyPrimitiveKind,
                       override val displayName: String) : TyClass(displayName), ITyPrimitive {

    override val kind = TyKind.Primitive

    override fun getSuperType(context: SearchContext): ITy? = null

    override fun doLazyInit(searchContext: SearchContext) { }

    override fun accept(visitor: ITyVisitor) {
        visitor.visitTy(this)
    }

    override fun equals(other: Any?): Boolean {
        return other is ITyPrimitive && other.primitiveKind == primitiveKind
    }

    override fun equals(context: SearchContext, other: ITy): Boolean {
        if (this === other) {
            return true
        }

        val resolvedOther = Ty.resolve(context, other)
        return resolvedOther is ITyPrimitive && resolvedOther.primitiveKind == primitiveKind
    }

    override fun hashCode(): Int {
        return primitiveKind.hashCode()
    }

    override fun contravariantOf(context: SearchContext, other: ITy, varianceFlags: Int): Boolean {
        return (other is ITyPrimitive && other.primitiveKind == primitiveKind) ||
                super.contravariantOf(context, other, varianceFlags)
    }

    override fun willResolve(context: SearchContext): Boolean {
        return false
    }

    override fun resolve(context: SearchContext, genericArgs: Array<out ITy>?): ITy {
        return this
    }
}

object TyPrimitiveSerializer : TySerializer<ITy>() {
    override fun deserializeTy(flags: Int, stream: StubInputStream): ITy {
        val primitiveKind = stream.readByte()
        return when (primitiveKind.toInt()) {
            TyPrimitiveKind.Boolean.ordinal -> Primitives.BOOLEAN
            TyPrimitiveKind.String.ordinal -> Primitives.STRING
            TyPrimitiveKind.Number.ordinal -> Primitives.NUMBER
            TyPrimitiveKind.Table.ordinal -> Primitives.TABLE
            TyPrimitiveKind.Function.ordinal -> Primitives.FUNCTION
            else -> Primitives.UNKNOWN
        }
    }

    override fun serializeTy(ty: ITy, stream: StubOutputStream) {
        when (ty) {
            is ITyPrimitive -> stream.writeByte(ty.primitiveKind.ordinal)
            else -> stream.writeByte(-1)
        }
    }
}

class TyDocPrimitiveTable(val luaDocPrimitiveTableTy: LuaDocPrimitiveTableTy) : TyPrimitiveClass(TyPrimitiveKind.Table, Constants.WORD_TABLE) {
    override fun processMember(context: SearchContext, name: String, deep: Boolean, process: ProcessTypeMember): Boolean {
        return process(this, luaDocPrimitiveTableTy)
    }

    override fun processIndexer(context: SearchContext, indexTy: ITy, exact: Boolean, deep: Boolean, process: ProcessTypeMember): Boolean {
        return process(this, luaDocPrimitiveTableTy)
    }

    override fun contravariantOf(context: SearchContext, other: ITy, varianceFlags: Int): Boolean {
        if (super.contravariantOf(context, other, varianceFlags)) {
            return true
        }

        if (varianceFlags and TyVarianceFlags.STRICT_UNKNOWN == 0) {
            val otherBase = if (other is ITyGeneric) other.base else other
            return other.kind == TyKind.Array
                    || otherBase.kind == TyKind.Class
                    || otherBase == Primitives.TABLE
        }

        return false
    }
}
