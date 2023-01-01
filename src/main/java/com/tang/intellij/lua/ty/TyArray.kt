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
import com.tang.intellij.lua.comment.psi.LuaDocArrTy
import com.tang.intellij.lua.search.SearchContext

interface ITyArray : ITy {
    val base: ITy
}

open class TyArray(override val base: ITy) : Ty(TyKind.Array), ITyArray {

    override fun equals(other: Any?): Boolean {
        return other is ITyArray && base == other.base
    }

    override fun equals(context: SearchContext, other: ITy): Boolean {
        if (this === other) {
            return true
        }

        val resolvedOther = Ty.resolve(context, other)
        return resolvedOther is ITyArray && base.equals(context, resolvedOther.base)
    }

    override fun hashCode(): Int {
        return base.hashCode() * 31 + 31
    }

    override fun contravariantOf(context: SearchContext, other: ITy, varianceFlags: Int): Boolean {
        if (super.contravariantOf(context, other, varianceFlags)) {
            return true
        }

        if (other !is ITyArray && (other !is ITyClass || !TyArray.isArray(context, other))) {
            return false
        }

        val resolvedBase = Ty.resolve(context, base)

        if (other is ITyArray) {
            return resolvedBase.equals(context, other.base)
                    || (varianceFlags and TyVarianceFlags.WIDEN_TABLES != 0 && resolvedBase.contravariantOf(context, other.base, varianceFlags))
        }

        var indexedMemberType: ITy = Primitives.VOID
        val membersContravariant = other.processMembers(context) { _, otherMember ->
            val index = otherMember.guessIndexType(context)

            if ((index is ITyPrimitive && index.primitiveKind == TyPrimitiveKind.Number)
                    || (index is TyPrimitiveLiteral && index.primitiveKind == TyPrimitiveKind.Number)) {
                val otherFieldTypes = context.withMultipleResults {
                    otherMember.guessType(context) ?: Primitives.UNKNOWN
                }.let {
                    if (it is TyMultipleResults) it.list else listOf(it)
                }

                otherFieldTypes.forEach { otherFieldTy ->
                    if (!resolvedBase.contravariantOf(context, otherFieldTy, varianceFlags)) {
                        return@processMembers false
                    }

                    indexedMemberType = indexedMemberType.union(context, otherFieldTy)
                }
            }
            true
        }

        if (!membersContravariant) {
            return false
        }

        return varianceFlags and TyVarianceFlags.WIDEN_TABLES != 0
                || Ty.resolve(context, resolvedBase).equals(context, indexedMemberType)
                || (resolvedBase.isUnknown && varianceFlags and TyVarianceFlags.STRICT_UNKNOWN == 0)
    }

    override fun substitute(context: SearchContext, substitutor: ITySubstitutor): ITy {
        val substitutedBase = TyMultipleResults.getResult(context, base.substitute(context, substitutor))

        return if (substitutedBase !== base) {
            TyArray(substitutedBase)
        } else {
            this
        }
    }

    override fun accept(visitor: ITyVisitor) {
        visitor.visitArray(this)
    }

    override fun acceptChildren(visitor: ITyVisitor) {
        base.accept(visitor)
    }

    override fun guessIndexerType(context: SearchContext, indexTy: ITy, exact: Boolean): ITy? {
        if ((!exact && Primitives.NUMBER.contravariantOf(context, indexTy, 0)) || Primitives.NUMBER == indexTy) {
            return base
        }

        return super<Ty>.guessIndexerType(context, indexTy, exact)
    }

    companion object {
        fun isArray(context: SearchContext, ty: ITyClass): Boolean {
            val resolvedTy = Ty.resolve(context, ty)

            if (resolvedTy is ITyArray) {
                return true
            }

            if (resolvedTy !is ITyClass) {
                return false
            }

            resolvedTy.lazyInit(context)

            if (resolvedTy.isShape(context)) {
                val indexes = mutableSetOf<Int>()
                var foundNumberIndexer = false

                val onlyIntegerKeys = resolvedTy.processMembers(context) { _, otherMember ->
                    val indexTy = otherMember.guessIndexType(context)

                    if (indexTy == null || indexTy !is TyPrimitiveLiteral || indexTy.primitiveKind != TyPrimitiveKind.Number) {
                        return@processMembers false
                    }

                    if (indexTy == Primitives.NUMBER) {
                        foundNumberIndexer = true
                    } else {
                        val index = indexTy.value.toIntOrNull()

                        if (index == null) {
                            return@processMembers false
                        }

                        indexes.add(index)
                    }

                    true
                }

                if (onlyIntegerKeys && !foundNumberIndexer) {
                    indexes.sorted().forEachIndexed { index, i ->
                        if (i != index + 1) {
                            return false
                        }
                    }
                }

                return onlyIntegerKeys
            }

            return false
        }
    }
}

object TyArraySerializer : TySerializer<ITyArray>() {
    override fun serializeTy(ty: ITyArray, stream: StubOutputStream) {
        Ty.serialize(ty.base, stream)
    }

    override fun deserializeTy(flags: Int, stream: StubInputStream): ITyArray {
        val base = Ty.deserialize(stream)
        return TyArray(base)
    }
}

class TyDocArray(val luaDocArrTy: LuaDocArrTy, base: ITy = luaDocArrTy.ty.getType()) : TyArray(base) {
    override fun processIndexer(context: SearchContext, indexTy: ITy, exact: Boolean, deep: Boolean, process: ProcessTypeMember): Boolean {
        if (exact) {
            if (Primitives.NUMBER.equals(context, indexTy)) {
                return process(this, luaDocArrTy)
            }
        } else if (Primitives.NUMBER.contravariantOf(context, indexTy, TyVarianceFlags.STRICT_UNKNOWN)) {
            return process(this, luaDocArrTy)
        }

        return true
    }

    override fun substitute(context: SearchContext, substitutor: ITySubstitutor): ITy {
        val substitutedBase = TyMultipleResults.getResult(context, base.substitute(context, substitutor))

        return if (substitutedBase !== base) {
            TyDocArray(luaDocArrTy, substitutedBase)
        } else {
            this
        }
    }
}
