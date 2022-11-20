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

class TyMultipleResults : Ty {
    val list: List<ITy>
    val variadic: Boolean

    constructor(iterable: Iterable<ITy>, variadic: Boolean): super(TyKind.MultipleResults) {
        list = iterable.toList()
        this.variadic = variadic
    }

    constructor(sequence: Sequence<ITy>, variadic: Boolean): super(TyKind.MultipleResults) {
        list = sequence.toList()
        this.variadic = variadic
    }

    override fun substitute(context: SearchContext, substitutor: ITySubstitutor): ITy {
        var resultsSubstituted = false
        var resultVariadic = variadic

        val substitutedResults = list.withIndex().flatMap {
            val substitutedResult = it.value.substitute(context, substitutor)

            if (substitutedResult !== it) {
                resultsSubstituted = true
            }

            if (substitutedResult is TyMultipleResults) {
                if (variadic) {
                    var aggregateTy: ITy = Primitives.VOID
                    substitutedResult.list.map {
                        aggregateTy = aggregateTy.union(context, it)
                        aggregateTy
                    }
                } else {
                    if (substitutedResult.variadic && it.index == list.lastIndex) {
                        resultVariadic = true
                    }

                    substitutedResult.list
                }
            } else listOf(substitutedResult)
        }
        return if (resultsSubstituted) {
            TyMultipleResults(substitutedResults, resultVariadic)
        } else {
            this
        }
    }

    override fun accept(visitor: ITyVisitor) {
        visitor.visitMultipleResults(this)
    }

    override fun acceptChildren(visitor: ITyVisitor) {
        list.forEach { it.accept(visitor) }
    }

    override fun contravariantOf(context: SearchContext, other: ITy, varianceFlags: Int): Boolean {
        val requiredSize = if (variadic) list.size - 1 else list.size
        val flattenedOther = TyMultipleResults.flatten(context, other)

        if (flattenedOther is TyMultipleResults) {
            if (flattenedOther.variadic) {
                if (!variadic) {
                    return false
                }
            } else {
                if (flattenedOther.list.size < requiredSize) {
                    return false
                }
            }

            for (i in 0 until flattenedOther.list.size) {
                val otherTy = flattenedOther.list[i]
                val thisTy = if (i >= list.size) {
                    if (variadic) list.last() else return true
                } else list[i]

                if (!thisTy.contravariantOf(context, otherTy, varianceFlags)) {
                    return false
                }
            }

            return true
        }

        return requiredSize <= 1 && list.first().contravariantOf(context, other, varianceFlags)
    }

    override fun hashCode(): Int {
        var hash = if (variadic) 1 else 0
        for (ty in list) {
            hash = hash * 31 + ty.hashCode()
        }
        return hash
    }

    override fun equals(other: Any?): Boolean {
        if (other is TyMultipleResults && other.variadic == variadic && other.list.size == list.size) {
            for (i in 0 until list.size) {
                if (list[i] != other.list[i]) {
                    return false
                }
            }
            return true
        }
        return super.equals(other)
    }

    override fun equals(context: SearchContext, other: ITy): Boolean {
        if (this === other) {
            return true
        }

        val resolvedOther = Ty.resolve(context, other)

        return resolvedOther is TyMultipleResults
                && resolvedOther.variadic == variadic
                && resolvedOther.list.size == list.size
                && resolvedOther.list.asSequence().zip(list.asSequence()).all { (otherTy, ty) -> otherTy.equals(context, ty) }
    }

    fun <R> convolve(context: SearchContext, other: TyMultipleResults, supportsMultipleResults: Boolean = false, transformer: (ITy, ITy?) -> R): Sequence<R> {
        return ConvolveSequence(context, this, other, supportsMultipleResults, transformer)
    }

    fun forResultPairs(context: SearchContext, other: TyMultipleResults, supportsMultipleResults: Boolean = false, process: (ITy, ITy?) -> Unit) {
        ConvolveSequence(context, this, other, supportsMultipleResults, process).iterator().let {
            while (it.hasNext()) {
                it.next()
            }
        }
    }

    private class ConvolveSequence<V>(
            private val context: SearchContext,
            private val multipleResults1: TyMultipleResults,
            private val multipleResults2: TyMultipleResults,
            private val supportsMultipleResults: Boolean,
            private val transform: (ITy, ITy?) -> V
    ) : Sequence<V> {
        override fun iterator(): Iterator<V> = object : Iterator<V> {
            var index1 = 0
            var index2 = 0
            val list1 = multipleResults1.list
            val list2 = multipleResults2.list
            val variadic1 = multipleResults1.variadic
            val variadic2 = multipleResults2.variadic

            override fun hasNext(): Boolean {
                return if (variadic1) {
                    (variadic2 && index2 < list2.size) || index2 <= list2.size
                } else {
                    index1 < list1.size
                }
            }

            override fun next(): V {
                val ty1 = if (multipleResults1.variadic && index1 >= list1.lastIndex) {
                    Primitives.NIL.union(context, list1.last())
                } else {
                    list1[index1]
                }

                val ty2 = if (supportsMultipleResults && index1 == list1.lastIndex) {
                    if (index2 >= list2.lastIndex) {
                        if (multipleResults2.variadic) {
                            TyMultipleResults(listOf(list2.last()), true)
                        } else {
                            list2.getOrNull(index2)
                        }
                    } else {
                        TyMultipleResults(list2.subList(index2, list2.size), multipleResults2.variadic)
                    }
                } else if (multipleResults2.variadic && index2 >= list2.lastIndex) {
                    Primitives.NIL.union(context, list2.last())
                } else {
                    list2.getOrNull(index2)
                }

                index1++
                index2++

                return transform(ty1, ty2)
            }
        }
    }

    companion object {
        fun getResult(context: SearchContext, ty: ITy, index: Int = 0): ITy {
            val flattenedTy = TyMultipleResults.flatten(context, ty)

            return if (flattenedTy is TyMultipleResults) {
                if (index < flattenedTy.list.lastIndex) {
                    getResult(context, flattenedTy.list.get(index))
                } else if (flattenedTy.variadic) {
                    val lastResult = flattenedTy.list.last()
                    val variadicTy = getResult(context, lastResult).union(context, getResult(context, lastResult, index - flattenedTy.list.lastIndex))
                    Primitives.NIL.union(context, variadicTy)
                } else {
                    getResult(context, flattenedTy.list.last(), index - flattenedTy.list.lastIndex)
                }
            } else if (index == 0) {
                flattenedTy
            } else {
                Primitives.NIL
            }
        }

        fun flatten(context: SearchContext, ty: ITy): ITy {
            if (ty !is TyUnion) {
                return ty
            }

            val tyList = mutableListOf<ITy>()
            var variadicTy: ITy? = null

            TyUnion.each(ty) {
                val resultCount: Number

                if (it is TyMultipleResults) {
                    val multipleResults = it

                    multipleResults.list.forEachIndexed { index, resultTy ->
                        if (index < tyList.size) {
                            tyList[index] = tyList[index].union(context, resultTy)
                        } else if (!multipleResults.variadic || index < multipleResults.list.lastIndex) {
                            tyList.add(variadicTy?.union(context, resultTy) ?: resultTy)
                        }
                    }

                    if (multipleResults.variadic) {
                        val multipleResultsVariadicTy = multipleResults.list.last()

                        for (i in multipleResults.list.size until tyList.size) {
                            tyList[i] = tyList[i].union(context, multipleResultsVariadicTy)
                        }

                        variadicTy = TyUnion.union(context, variadicTy, multipleResultsVariadicTy)
                        resultCount = tyList.size
                    } else {
                        resultCount = multipleResults.list.size
                    }
                } else {
                    resultCount = 1

                    if (tyList.isEmpty()) {
                        tyList.add(it)
                    } else {
                        tyList[0] = tyList[0].union(context, it)
                    }
                }

                for (i in resultCount until tyList.size) {
                    tyList[i] = TyUnion.union(context, tyList[i], Primitives.NIL)
                }
            }

            if (tyList.size == 1 && variadicTy == null) {
                return tyList[0]
            }

            variadicTy?.let { tyList.add(it) }
            return TyMultipleResults(tyList.toList(), variadicTy != null)
        }
    }
}

object TyMultipleResultsSerializer : TySerializer<TyMultipleResults>() {
    override fun deserializeTy(flags: Int, stream: StubInputStream): TyMultipleResults {
        val size = stream.readByte().toInt()
        val list = mutableListOf<ITy>()
        for (i in 0 until size) list.add(Ty.deserialize(stream))
        val variadic = stream.readBoolean()
        return TyMultipleResults(list, variadic)
    }

    override fun serializeTy(ty: TyMultipleResults, stream: StubOutputStream) {
        stream.writeByte(ty.list.size)
        ty.list.forEach { Ty.serialize(it, stream) }
        stream.writeBoolean(ty.variadic)
    }
}
