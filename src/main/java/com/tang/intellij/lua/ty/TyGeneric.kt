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
import com.intellij.util.io.StringRef
import com.tang.intellij.lua.comment.psi.LuaDocGenericDef
import com.tang.intellij.lua.comment.psi.LuaDocGenericTableTy
import com.tang.intellij.lua.psi.LuaClassMember
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.readTyNullable
import com.tang.intellij.lua.stubs.writeTyNullable

fun genericParameterName(def: LuaDocGenericDef): String {
    return "${def.id.text}@${def.node.startOffset}@${def.containingFile.name}"
}

class TyGenericParameter(val name: String, varName: String, superClass: ITy? = null) : TySerializedClass(name, emptyArray(), varName, superClass, null) {
    constructor(def: LuaDocGenericDef) : this(genericParameterName(def), def.id.text, def.classRef?.let { Ty.create(it) })

    override fun equals(other: Any?): Boolean {
        return other is TyGenericParameter
                && super.equals(other)
                && superClass?.equals(other.superClass) ?: (other.superClass == null)
    }

    override fun hashCode(): Int {
        return super.hashCode() * 31 + (superClass?.hashCode() ?: 0)
    }

    override val kind: TyKind
        get() = TyKind.GenericParam

    override fun processMembers(context: SearchContext, processor: (ITy, LuaClassMember) -> Boolean, deep: Boolean): Boolean {
        val superType = getSuperClass(context)

        if (superType is ITyClass) {
            return superType.processMembers(context, processor, deep)
        } else if (superType is ITyGeneric) {
            return (superType.base as? ITyClass)?.processMembers(context, processor, deep) ?: true
        }

        return true
    }

    override fun toString(): String {
        return displayName
    }

    override fun contravariantOf(other: ITy, context: SearchContext, flags: Int): Boolean {
        return if (flags and TyVarianceFlags.ABSTRACT_PARAMS != 0) {
            superClass?.contravariantOf(other, context, flags) ?: true
        } else super.contravariantOf(other, context, flags)
    }

    override fun doLazyInit(searchContext: SearchContext) {}

    override fun willResolve(context: SearchContext): Boolean {
        return false
    }

    override fun resolve(context: SearchContext, genericArgs: Array<out ITy>?): ITy {
        return this
    }
}

object TyGenericParamSerializer : TySerializer<TyGenericParameter>() {
    override fun deserializeTy(flags: Int, stream: StubInputStream): TyGenericParameter {
        val name = StringRef.toString(stream.readName())
        val varName = StringRef.toString(stream.readName())
        val superClass = stream.readTyNullable()
        return TyGenericParameter(name, varName, superClass)
    }

    override fun serializeTy(ty: TyGenericParameter, stream: StubOutputStream) {
        stream.writeName(ty.name)
        stream.writeName(ty.varName)
        stream.writeTyNullable(ty.superClass)
    }
}

interface ITyGeneric : ITyResolvable {
    val args: Array<out ITy>
    val base: ITy

    fun getArgTy(index: Int): ITy {
        return args.elementAtOrNull(index) ?: Ty.UNKNOWN
    }

    override fun getMemberSubstitutor(context: SearchContext): ITySubstitutor {
        val resolvedBase = TyAliasSubstitutor.substitute(base, context)
        val baseParams = resolvedBase.getParams(context) ?: arrayOf()
        return TyChainSubstitutor.chain(super.getMemberSubstitutor(context), TyParameterSubstitutor.withArgs(context, baseParams, args))!!
    }

    override fun willResolve(context: SearchContext): Boolean {
        TyUnion.each(base) {
            if ((it as? ITyResolvable)?.willResolve(context) == true) {
                return true
            }
        }

        return false
    }

    override fun resolve(context: SearchContext, genericArgs: Array<out ITy>?): ITy {
        val resolved = (base as? ITyResolvable)?.resolve(context, args) ?: base
        return if (resolved !== base) resolved else this
    }
}

open class TyGeneric(override val args: Array<out ITy>, override val base: ITy) : Ty(TyKind.Generic), ITyGeneric {

    override fun equals(other: Any?): Boolean {
        return other is ITyGeneric && other.base == base && other.displayName == displayName
    }

    override fun equals(other: ITy, context: SearchContext): Boolean {
        if (this === other) {
            return true
        }

        val resolvedOther = Ty.resolve(other, context)

        if (resolvedOther is ITyGeneric && args.size == resolvedOther.args.size && base.equals(resolvedOther.base, context)) {
            val allParamsEqual = args.asSequence().zip(resolvedOther.args.asSequence()).all { (param, otherParam) ->
                param.equals(otherParam)
            }

            if (allParamsEqual) {
                return true
            }
        }

        if (isShape(context) && resolvedOther.isShape(context)) {
            return contravariantOf(resolvedOther, context, 0)
                    && resolvedOther.contravariantOf(this, context, 0)
        }

        return false
    }

    override fun hashCode(): Int {
        return displayName.hashCode()
    }

    override fun getSuperClass(context: SearchContext): ITy? {
        val superClass = base.getSuperClass(context)

        if (superClass is ITyGeneric) {
            val baseParams = base.getParams(context)

            if (baseParams != null) {
                return superClass.substitute(TyParameterSubstitutor.withArgs(context, baseParams, args))
            }
        }

        return superClass
    }

    override fun contravariantOf(other: ITy, context: SearchContext, flags: Int): Boolean {
        val resolvedBase = Ty.resolve(base, context)
        val resolvedOther = Ty.resolve(other, context)

        if (resolvedBase is ITyAlias) {
            TyUnion.each(resolvedBase.ty.substitute(getMemberSubstitutor(context))) {
                if (it.contravariantOf(resolvedOther, context, flags)) {
                    return true
                }
            }

            return false
        }

        if (resolvedBase.isShape(context)) {
            val memberSubstitutor = getMemberSubstitutor(context)
            val otherMemberSubstitutor = resolvedOther.getMemberSubstitutor(context)

            return processMembers(context, { _, classMember ->
                val memberTy = classMember.guessType(context)?.substitute(memberSubstitutor)

                if (memberTy == null) {
                    return@processMembers true
                }

                val indexTy = classMember.guessIndexType(context)
                val otherMember = if (indexTy != null) {
                    resolvedOther.findIndexer(indexTy, context, false)
                } else {
                    classMember.name?.let { resolvedOther.findMember(it, context) }
                }

                if (otherMember == null) {
                    return@processMembers TyUnion.find(memberTy, TyNil::class.java) != null
                }

                val otherMemberTy = (otherMember.guessType(context) ?: Ty.UNKNOWN).let {
                    if (otherMemberSubstitutor != null) it.substitute(otherMemberSubstitutor) else it
                }

                memberTy.contravariantOf(otherMemberTy, context, flags)
            }, true)
        }

        if (resolvedOther is ITyArray) {
            return if (resolvedBase == Ty.TABLE && args.size == 2) {
                val keyTy = args.first()
                val valueTy = args.last()
                val resolvedOtherBase = Ty.resolve(resolvedOther.base, context)
                return (keyTy == Ty.NUMBER || (keyTy is TyUnknown && flags and TyVarianceFlags.STRICT_UNKNOWN == 0))
                        && (valueTy == resolvedOtherBase || (flags and TyVarianceFlags.WIDEN_TABLES != 0 && valueTy.contravariantOf(resolvedOtherBase, context, flags)))
            } else false
        }

        var otherBase: ITy? = null
        var otherArgs: Array<out ITy>? =  null
        var contravariantParams = false

        if (resolvedOther is ITyGeneric) {
            otherBase = resolvedOther.base
            otherArgs = resolvedOther.args
        } else if (resolvedBase == Ty.TABLE && args.size == 2) {
            if (resolvedOther == Ty.TABLE) {
                return args.first() is TyUnknown && args.last() is TyUnknown
            }

            if (resolvedOther.isShape(context)) {
                val genericTable = createTableGenericFromMembers(resolvedOther, context)
                otherBase = genericTable.base
                otherArgs = genericTable.args
                contravariantParams = flags and TyVarianceFlags.WIDEN_TABLES != 0
            }
        } else if (resolvedOther is ITyClass) {
            otherBase = resolvedOther
            otherArgs = resolvedOther.getParams(context)
        }

        if (otherBase != null) {
            if (otherBase.equals(resolvedBase, context)) {
                return args.size == otherArgs?.size && args.asSequence().zip(otherArgs.asSequence()).all { (arg, otherArg) ->
                    // Args are always invariant as we don't support use-site variance nor immutable/read-only annotations
                    arg.equals(otherArg, context)
                            || (flags and TyVarianceFlags.STRICT_UNKNOWN == 0 && otherArg is TyUnknown)
                            || (
                                (contravariantParams || (flags and TyVarianceFlags.ABSTRACT_PARAMS != 0 && arg is TyGenericParameter))
                                && arg.contravariantOf(otherArg, context, flags)
                            )
                }
            }
        }

        return super.contravariantOf(resolvedOther, context, flags)
    }

    override fun accept(visitor: ITyVisitor) {
        visitor.visitGeneric(this)
    }

    override fun substitute(substitutor: ITySubstitutor): ITy {
        return substitutor.substitute(this)
    }

    override fun findMember(name: String, searchContext: SearchContext): LuaClassMember? {
        return base.findMember(name, searchContext)
    }

    override fun findIndexer(indexTy: ITy, searchContext: SearchContext, exact: Boolean): LuaClassMember? {
        return base.findIndexer(indexTy, searchContext, exact)
    }

    override fun isShape(searchContext: SearchContext): Boolean {
        return base.isShape(searchContext)
    }

    override fun guessMemberType(name: String, searchContext: SearchContext): ITy? {
        if (base == Ty.TABLE && args.size == 2 && (args[0] == Ty.STRING
                        || args[0].contravariantOf(TyPrimitiveLiteral.getTy(TyPrimitiveKind.String, name), searchContext, 0))) {
            return args[1]
        }

        return super<Ty>.guessMemberType(name, searchContext)
    }

    override fun guessIndexerType(indexTy: ITy, searchContext: SearchContext, exact: Boolean): ITy? {
        if (base == Ty.TABLE && args.size == 2 && ((!exact && args[0].contravariantOf(indexTy, searchContext, 0)) || indexTy == args[0])) {
            return args[1]
        }

        return super<Ty>.guessIndexerType(indexTy, searchContext, exact)
    }

    override fun processMembers(context: SearchContext, processor: (ITy, LuaClassMember) -> Boolean, deep: Boolean): Boolean {
        if (!base.processMembers(context, { _, classMember -> processor(this, classMember) }, false)) {
            return false
        }

        // super
        if (deep) {
            return Ty.processSuperClasses(this, context) {
                it.processMembers(context, processor, false)
            }
        }

        return true
    }
}

object TyGenericSerializer : TySerializer<ITyGeneric>() {
    override fun deserializeTy(flags: Int, stream: StubInputStream): ITyGeneric {
        val base = Ty.deserialize(stream)
        val size = stream.readByte()
        val params = mutableListOf<ITy>()
        for (i in 0 until size) {
            params.add(Ty.deserialize(stream))
        }
        return TyGeneric(params.toTypedArray(), base)
    }

    override fun serializeTy(ty: ITyGeneric, stream: StubOutputStream) {
        Ty.serialize(ty.base, stream)
        stream.writeByte(ty.args.size)
        ty.args.forEach { Ty.serialize(it, stream) }
    }
}

class TyDocTableGeneric(
        val genericTableTy: LuaDocGenericTableTy,
        val keyType: ITy = genericTableTy.keyType?.getType() ?: Ty.UNKNOWN,
        val valueType: ITy = genericTableTy.valueType?.getType() ?: Ty.UNKNOWN
) : TyGeneric(
        arrayOf(keyType, valueType),
        Ty.TABLE
) {
    override fun findMember(name: String, searchContext: SearchContext): LuaClassMember? {
        Ty.eachResolved(keyType, searchContext) {
            if ((it is ITyPrimitive && it.primitiveKind == TyPrimitiveKind.String)
                    || (it is TyPrimitiveLiteral && it.primitiveKind == TyPrimitiveKind.String && it.value == name)) {
                return genericTableTy
            }
        }

        return null
    }

    override fun findIndexer(indexTy: ITy, searchContext: SearchContext, exact: Boolean): LuaClassMember? {
        if (exact) {
            Ty.eachResolved(keyType, searchContext) {
                if (it.equals(indexTy, searchContext)) {
                    return genericTableTy
                }
            }
        } else if (keyType.contravariantOf(indexTy, searchContext, TyVarianceFlags.STRICT_UNKNOWN)) {
            return genericTableTy
        }

        return null
    }
}

