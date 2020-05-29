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
import com.tang.intellij.lua.psi.LuaClassMember
import com.tang.intellij.lua.search.SearchContext
import java.util.*

private val displayNameComparator: Comparator<ITy> = Comparator { a, b -> a.displayName.compareTo(b.displayName) }

class TyUnion private constructor(private val childSet: TreeSet<ITy>) : Ty(TyKind.Union) {
    fun getChildTypes() = childSet

    val size:Int
        get() = childSet.size

    override val booleanType: ITy
        get() {
            var resolvedType: ITy? = null
            childSet.forEach {
                when (it.booleanType) {
                    Ty.TRUE -> {
                        if (resolvedType == Ty.FALSE) return Ty.BOOLEAN
                        resolvedType = Ty.TRUE
                    }
                    Ty.FALSE -> {
                        if (resolvedType == Ty.TRUE) return Ty.BOOLEAN
                        resolvedType = Ty.FALSE
                    }
                    else -> return Ty.BOOLEAN
                }
            }
            return resolvedType ?: Ty.BOOLEAN
        }

    override fun union(ty: ITy): TyUnion {
        if (ty == Ty.VOID) {
            return this
        }

        val unionTys = mutableListOf<ITy>()
        unionTys.addAll(childSet)

        if (ty is TyUnion) {
            unionTys.addAll(ty.childSet)
        } else {
            unionTys.add(ty)
        }

        return TyUnion.union(unionTys) as TyUnion
    }

    override fun not(ty: ITy): ITy {
        val unionTys = mutableListOf<ITy>()
        unionTys.addAll(childSet)

        val notTypes = if (ty is TyUnion) ty.childSet else listOf(ty)

        notTypes.forEach {
            unionTys.remove(it)

            if (it == Ty.FALSE) {
                if (unionTys.remove(Ty.BOOLEAN)) {
                    unionTys.add(Ty.TRUE)
                }
            } else if (it == Ty.TRUE) {
                if (unionTys.remove(Ty.BOOLEAN)) {
                    unionTys.add(Ty.FALSE)
                }
            } else if (it == Ty.BOOLEAN) {
                unionTys.remove(Ty.TRUE)
                unionTys.remove(Ty.FALSE)
            }
        }

        return if (unionTys.size == 1) {
            unionTys.first()
        } else if (unionTys.size == 0) {
            Ty.VOID
        } else {
            TyUnion.union(unionTys)
        }
    }

    override fun contravariantOf(other: ITy, context: SearchContext, flags: Int): Boolean {
        return super.contravariantOf(other, context, flags)
                || childSet.any { type -> type.contravariantOf(other, context, flags) }
    }

    override fun substitute(substitutor: ITySubstitutor): ITy {
        var substituted = false
        val substitutedChildren = childSet.map {
            val substitutedChild = it.substitute(substitutor)

            if (substitutedChild !== it) {
                substituted = true
            }

            substitutedChild
        }

        return if (substituted) {
            TyUnion.union(substitutedChildren)
        } else this
    }

    override fun findMember(name: String, searchContext: SearchContext): LuaClassMember? {
        return childSet.firstOrNull()?.findMember(name, searchContext)
    }

    override fun findIndexer(indexTy: ITy, searchContext: SearchContext): LuaClassMember? {
        return childSet.firstOrNull()?.findIndexer(indexTy, searchContext)
    }

    override fun guessMemberType(name: String, searchContext: SearchContext): ITy? {
        var ty: ITy? = null

        childSet.forEach {
            val valueTy = it.guessMemberType(name, searchContext)

            if (valueTy != null) {
                ty = ty?.union(valueTy) ?: valueTy
            }
        }

        return ty
    }

    override fun guessIndexerType(indexTy: ITy, searchContext: SearchContext): ITy? {
        var ty: ITy? = null

        childSet.forEach {
            val valueTy = it.guessIndexerType(indexTy, searchContext)

            if (valueTy != null) {
                ty = ty?.union(valueTy) ?: valueTy
            }
        }

        return ty
    }

    override fun accept(visitor: ITyVisitor) {
        visitor.visitUnion(this)
    }

    override fun acceptChildren(visitor: ITyVisitor) {
        childSet.forEach { it.accept(visitor) }
    }

    override fun equals(other: Any?): Boolean {
        return other is TyUnion && other.hashCode() == hashCode()
    }

    override fun hashCode(): Int {
        var code = 0
        childSet.forEach { code = code * 31 + it.hashCode() }
        return code
    }

    companion object {
        fun <T : ITy> find(ty: ITy, clazz: Class<T>): T? {
            if (clazz.isInstance(ty))
                return clazz.cast(ty)
            each(ty) {
                if (clazz.isInstance(it)) {
                    return clazz.cast(it)
                }
            }
            return null
        }

        inline fun each(ty: ITy, fn: (ITy) -> Unit) {
            if (ty is TyUnion) {
                for (child in ty.getChildTypes().toTypedArray()) {
                    fn(child)
                }
            } else if (ty == Ty.BOOLEAN) {
                fn(Ty.TRUE)
                fn(Ty.FALSE)
            } else {
                fn(ty)
            }
        }

        // used by ver.2017
        @Suppress("unused")
        fun eachPerfect(ty: ITy, process: (ITy) -> Boolean) {
            if (ty is TyUnion) {
                val list = ty.childSet.sorted()
                for (iTy in list) {
                    if (!process(iTy))
                        break
                }
            } else process(ty)
        }

        fun union(t1: ITy, t2: ITy): ITy {
            return when {
                t1 == t2 -> t1
                t1 is TyUnknown || t2 is TyUnknown -> Ty.UNKNOWN
                isInvalid(t1) -> t2
                isInvalid(t2) -> t1
                t1 is TyUnion -> t1.union(t2)
                t2 is TyUnion -> t2.union(t1)
                else -> {
                    TyUnion.union(listOf(t1, t2))
                }
            }
        }

        fun union(tys: Iterable<ITy>): ITy {
            val childSet = sortedSetOf(displayNameComparator)

            tys.forEach {
                if (it is TyUnion) {
                    childSet.addAll(it.childSet)
                } else if (it != Ty.VOID) {
                    childSet.add(it)
                }
            }

            if (childSet.contains(Ty.TRUE) && childSet.contains(Ty.FALSE)) {
                childSet.add(Ty.BOOLEAN)
            }

            if (childSet.contains(Ty.BOOLEAN)) {
                childSet.remove(Ty.TRUE)
                childSet.remove(Ty.FALSE)
            }

            return if (childSet.size == 1) {
                childSet.first()
            } else {
                TyUnion(childSet)
            }
        }

        // NOTE: This is *not* set subtraction because TyUnion can only represent a union of types, not a true type set.
        // i.e. if t2 contains a type that is covariant of a type in t1, this case is not handled.
        fun not(t1: ITy, t2: ITy): ITy {
            return when {
                t1 is TyUnion -> t1.not(t2)
                t1 == t2 || t2 is TyUnknown || (t2 is TyUnion && t2.childSet.contains(t1)) -> Ty.VOID
                else -> t1
            }
        }

        fun getPerfectClass(ty: ITy): ITyClass? {
            var clazz: ITyClass? = null
            var anonymous: ITyClass? = null
            var global: ITyClass? = null
            each(ty) {
                if (it is ITyClass) {
                    when {
                        it.isAnonymous -> anonymous = it
                        it.isGlobal -> global = it
                        else -> clazz = it
                    }
                }
                clazz == null
            }
            return clazz ?: global ?: anonymous
        }
    }
}

object TyUnionSerializer : TySerializer<TyUnion>() {
    override fun serializeTy(ty: TyUnion, stream: StubOutputStream) {
        stream.writeInt(ty.size)
        TyUnion.each(ty) { Ty.serialize(it, stream) }
    }

    override fun deserializeTy(flags: Int, stream: StubInputStream): TyUnion {
        val tys = mutableListOf<ITy>()
        val size = stream.readInt()
        for (i in 0 until size) {
            tys.add(Ty.deserialize(stream))
        }
        return TyUnion.union(tys) as TyUnion
    }
}
