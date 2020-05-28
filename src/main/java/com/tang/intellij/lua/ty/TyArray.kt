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
import com.tang.intellij.lua.search.SearchContext

interface ITyArray : ITy {
    val base: ITy
}

class TyArray(override val base: ITy) : Ty(TyKind.Array), ITyArray {

    override fun equals(other: Any?): Boolean {
        return other is ITyArray && base == other.base
    }

    override fun hashCode(): Int {
        return displayName.hashCode()
    }

    override fun contravariantOf(other: ITy, context: SearchContext, flags: Int): Boolean {
        if (super.contravariantOf(other, context, flags) || (other is ITyArray && base.contravariantOf(other.base, context, flags))) {
            return true
        }

        if (other !is ITyClass || !TyArray.isArray(other, context)) {
            return false
        }

        return other.processMembers(context) { _, otherMember ->
            val otherFieldTypes = otherMember.guessType(context).let {
                if (it is TyMultipleResults) it.list else listOf(it)
            }

            otherFieldTypes.forEach { otherFieldTy ->
                if (!base.contravariantOf(otherFieldTy, context, flags)) {
                    return@processMembers false
                }
            }

            true
        }
    }

    override fun substitute(substitutor: ITySubstitutor): ITy {
        return TyArray(base.substitute(substitutor))
    }

    override fun accept(visitor: ITyVisitor) {
        visitor.visitArray(this)
    }

    override fun acceptChildren(visitor: ITyVisitor) {
        base.accept(visitor)
    }

    override fun guessIndexerType(indexTy: ITy, searchContext: SearchContext): ITy? {
        if (Ty.NUMBER.contravariantOf(indexTy, searchContext, 0)) {
            return base
        }

        return super<Ty>.guessIndexerType(indexTy, searchContext)
    }

    companion object {
        fun isArray(ty: ITyClass, context: SearchContext): Boolean {
            ty.lazyInit(context)

            if (ty is TyTable || ty.flags and TyFlags.SHAPE != 0) {
                val indexes = mutableSetOf<Int>()
                var foundNumberIndexer = false

                val onlyIntegerKeys = ty.processMembers(context) { _, otherMember ->
                    val indexTy = otherMember.guessIndexType(context)

                    if (indexTy == null || indexTy !is TyPrimitiveLiteral || indexTy.primitiveKind != TyPrimitiveKind.Number) {
                        return@processMembers false
                    }

                    if (indexTy == Ty.NUMBER) {
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
