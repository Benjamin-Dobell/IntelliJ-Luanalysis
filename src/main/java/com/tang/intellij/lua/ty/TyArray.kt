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

    override fun equals(other: ITy, context: SearchContext): Boolean {
        if (this === other) {
            return true
        }

        val resolvedOther = Ty.resolve(other, context)
        return resolvedOther is ITyArray && base.equals(resolvedOther.base, context)
    }

    override fun hashCode(): Int {
        return displayName.hashCode()
    }

    override fun contravariantOf(other: ITy, context: SearchContext, flags: Int): Boolean {
        if (super.contravariantOf(other, context, flags)) {
            return true
        }

        if (other !is ITyArray && (other !is ITyClass || !TyArray.isArray(other, context))) {
            return false
        }

        val resolvedBase = Ty.resolve(base, context)

        if (other is ITyArray) {
            return resolvedBase.equals(other.base, context)
                    || (flags and TyVarianceFlags.WIDEN_TABLES != 0 && resolvedBase.contravariantOf(other.base, context, flags))
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
                    if (!resolvedBase.contravariantOf(otherFieldTy, context, flags)) {
                        return@processMembers false
                    }

                    indexedMemberType = indexedMemberType.union(otherFieldTy, context)
                }
            }
            true
        }

        if (!membersContravariant) {
            return false
        }

        return flags and TyVarianceFlags.WIDEN_TABLES != 0
                || Ty.resolve(resolvedBase, context).equals(indexedMemberType, context)
                || (resolvedBase.isUnknown && flags and TyVarianceFlags.STRICT_UNKNOWN == 0)
    }

    override fun substitute(substitutor: ITySubstitutor): ITy {
        val substitutedBase = TyMultipleResults.getResult(substitutor.searchContext, base.substitute(substitutor))

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

    override fun guessIndexerType(indexTy: ITy, searchContext: SearchContext, exact: Boolean): ITy? {
        if ((!exact && Primitives.NUMBER.contravariantOf(indexTy, searchContext, 0)) || Primitives.NUMBER == indexTy) {
            return base
        }

        return super<Ty>.guessIndexerType(indexTy, searchContext, exact)
    }

    companion object {
        fun isArray(ty: ITyClass, context: SearchContext): Boolean {
            val resolvedTy = Ty.resolve(ty, context)

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
            if (Primitives.NUMBER.equals(indexTy, context)) {
                return process(this, luaDocArrTy)
            }
        } else if (Primitives.NUMBER.contravariantOf(indexTy, context, TyVarianceFlags.STRICT_UNKNOWN)) {
            return process(this, luaDocArrTy)
        }

        return true
    }

    override fun substitute(substitutor: ITySubstitutor): ITy {
        val substitutedBase = TyMultipleResults.getResult(substitutor.searchContext, base.substitute(substitutor))

        return if (substitutedBase !== base) {
            TyDocArray(luaDocArrTy, substitutedBase)
        } else {
            this
        }
    }
}
