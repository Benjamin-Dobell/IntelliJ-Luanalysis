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
import com.tang.intellij.lua.psi.LuaScopedTypeTree
import com.tang.intellij.lua.psi.getFileIdentifier
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.readTyNullable
import com.tang.intellij.lua.stubs.writeTyNullable

fun genericParameterName(def: LuaDocGenericDef): String {
    return "${def.id.text}@${def.node.startOffset}@${def.containingFile.getFileIdentifier()}"
}

class TyGenericParameter(name: String, val scopeName: String?, override val varName: String, superClass: ITy? = null) : TySerializedClass(name, null, varName, superClass, null) {
    constructor(def: LuaDocGenericDef) : this(
        genericParameterName(def),
        LuaScopedTypeTree.get(def.containingFile)?.findScope(def)?.scope?.name,
        def.id.text,
        def.superClass?.getType()
    )

    override fun equals(context: SearchContext, other: ITy, equalityFlags: Int): Boolean {
        val abstract = context.abstractGenericScopeNames?.contains(scopeName) ?: false

        if (abstract) {
            val superEquals = (other is TyGenericParameter
                && superClass?.let { superClass ->
                other.superClass?.let { otherSuperClass ->
                    superClass.equals(context, otherSuperClass, equalityFlags)
                } ?: false
            } ?: (other.superClass == null))

            if (superEquals) {
                return true
            }
        }

        return super.equals(context, other, equalityFlags)
    }

    override val kind: TyKind
        get() = TyKind.GenericParam

    override fun processMembers(context: SearchContext, deep: Boolean, process: ProcessTypeMember): Boolean {
        val superType = getSuperType(context)

        if (superType is ITyClass) {
            return superType.processMembers(context, deep, process)
        } else if (superType is ITyGeneric) {
            return (superType.base as? ITyClass)?.processMembers(context, deep, process) ?: true
        }

        return true
    }

    override fun contravariantOf(context: SearchContext, other: ITy, varianceFlags: Int): Boolean {
        val abstract = context.abstractGenericScopeNames?.contains(scopeName) ?: false

        return if (abstract) {
            getSuperType(context)?.contravariantOf(context, other, varianceFlags) ?: true
        } else {
            super.contravariantOf(context, other, varianceFlags)
        }
    }

    override fun doLazyInit(searchContext: SearchContext) = Unit

    override fun willResolve(context: SearchContext): Boolean {
        return false
    }

    override fun resolve(context: SearchContext, genericArgs: Array<out ITy>?): ITy {
        return this
    }

    override fun substitute(context: SearchContext, substitutor: ITySubstitutor): ITy {
        val substitutedTy = super.substitute(context, substitutor)

        if (substitutedTy !== this) {
            return substitutedTy
        }

        val superTy = getSuperType(context)
        val substitutedSuperTy = superTy?.substitute(context, substitutor)

        if (superTy !== substitutedSuperTy) {
            return TyGenericParameter(className, scopeName, varName, substitutedSuperTy)
        }

        return this
    }
}

object TyGenericParamSerializer : TySerializer<TyGenericParameter>() {
    override fun deserializeTy(flags: Int, stream: StubInputStream): TyGenericParameter {
        val className = StringRef.toString(stream.readName())
        val scopeName = StringRef.toString(stream.readName())
        val varName = StringRef.toString(stream.readName())
        val superClass = stream.readTyNullable()
        return TyGenericParameter(className, scopeName, varName, superClass)
    }

    override fun serializeTy(ty: TyGenericParameter, stream: StubOutputStream) {
        stream.writeName(ty.className)
        stream.writeName(ty.scopeName)
        stream.writeName(ty.varName)
        stream.writeTyNullable(ty.superClass)
    }
}

interface ITyGeneric : ITyResolvable {
    val args: Array<out ITy>
    val base: ITy

    fun getArgTy(index: Int): ITy {
        return args.elementAtOrNull(index) ?: Primitives.UNKNOWN
    }

    override fun getMemberSubstitutor(context: SearchContext): ITySubstitutor {
        val resolvedBase = TyAliasSubstitutor.substitute(context, base)
        val baseParams = resolvedBase.getParams(context) ?: arrayOf()
        val parameterSubstitutor = TyGenericParameterSubstitutor.withArgs(baseParams, args)
        return super.getMemberSubstitutor(context)?.let {
            TyChainSubstitutor.chain(it, parameterSubstitutor)
        } ?: parameterSubstitutor
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
        return if (resolved != base) resolved else this
    }
}

open class TyGeneric(override val args: Array<out ITy>, override val base: ITy) : Ty(TyKind.Generic), ITyGeneric {

    override fun equals(context: SearchContext, other: ITy, equalityFlags: Int): Boolean {
        if (this === other) {
            return true
        }

        val resolved = Ty.resolve(context, this)

        if (resolved !== this) {
            return resolved.equals(context, other, equalityFlags)
        }

        val resolvedOther = Ty.resolve(context, other)

        if (resolvedOther is ITyGeneric && args.size == resolvedOther.args.size && base.equals(context, resolvedOther.base, equalityFlags)) {
            val allParamsEqual = args.asSequence().zip(resolvedOther.args.asSequence()).all { (param, otherParam) ->
                param.equals(otherParam)
            }

            if (allParamsEqual) {
                return true
            }
        }

        if (isShape(context) && resolvedOther.isShape(context)) {
            return contravariantOf(context, resolvedOther, 0)
                    && resolvedOther.contravariantOf(context, this, 0)
        }

        return false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        return other is ITyGeneric
            && other.args.size == args.size
            && other.base == base
            && args.withIndex().all { (index, ty) -> ty.equals(other.args[index]) }
    }

    override fun hashCode(): Int {
        return args.fold(base.hashCode()) { acc, iTy -> 31 * acc + iTy.hashCode() }
    }

    override fun getSuperType(context: SearchContext): ITy? {
        val superClass = base.getSuperType(context)

        if (superClass is ITyGeneric) {
            val baseParams = base.getParams(context)

            if (baseParams != null) {
                return superClass.substitute(context, TyGenericParameterSubstitutor.withArgs(baseParams, args))
            }
        }

        return superClass
    }

    override fun contravariantOf(context: SearchContext, other: ITy, varianceFlags: Int): Boolean {
        val resolvedBase = Ty.resolve(context, base)
        val resolvedOther = Ty.resolve(context, other)

        if (resolvedBase is ITyAlias) {
            TyUnion.each(resolvedBase.ty.substitute(context, getMemberSubstitutor(context))) {
                if (it.contravariantOf(context, resolvedOther, varianceFlags)) {
                    return true
                }
            }

            return false
        }

        if (resolvedBase.isShape(context)) {
            val memberSubstitutor = getMemberSubstitutor(context)
            val otherMemberSubstitutor = resolvedOther.getMemberSubstitutor(context)

            return processMembers(context, true) { _, classMember ->
                val memberTy = classMember.guessType(context)?.substitute(context, memberSubstitutor)

                if (memberTy == null) {
                    return@processMembers true
                }

                val indexTy = classMember.guessIndexType(context)
                val otherMember = if (indexTy != null) {
                    resolvedOther.findIndexer(context, indexTy, false)
                } else {
                    classMember.name?.let { resolvedOther.findEffectiveMember(context, it) }
                }

                if (otherMember == null) {
                    return@processMembers TyUnion.find(memberTy, TyNil::class.java) != null
                }

                val otherMemberTy = (otherMember.guessType(context) ?: Primitives.UNKNOWN).let {
                    if (otherMemberSubstitutor != null) it.substitute(context, otherMemberSubstitutor) else it
                }

                memberTy.contravariantOf(context, otherMemberTy, varianceFlags)
            }
        }

        if (resolvedOther is ITyArray) {
            return if (resolvedBase == Primitives.TABLE && args.size == 2) {
                val keyTy = args.first()
                val valueTy = args.last()
                val resolvedOtherBase = Ty.resolve(context, resolvedOther.base)
                return (keyTy == Primitives.NUMBER || (keyTy.isUnknown && varianceFlags and TyVarianceFlags.STRICT_UNKNOWN == 0))
                        && (valueTy == resolvedOtherBase || (varianceFlags and TyVarianceFlags.WIDEN_TABLES != 0 && valueTy.contravariantOf(
                    context,
                    resolvedOtherBase,
                    varianceFlags
                )))
            } else false
        }

        var otherBase: ITy? = null
        var otherArgs: Array<out ITy>? =  null
        var contravariantParams = false

        if (resolvedOther is ITyGeneric) {
            otherBase = resolvedOther.base
            otherArgs = resolvedOther.args
        } else if (resolvedBase == Primitives.TABLE && args.size == 2) {
            if (resolvedOther == Primitives.TABLE) {
                return args.first().isUnknown && args.last().isUnknown
            }

            if (resolvedOther.isShape(context)) {
                val genericTable = createTableGenericFromMembers(context, resolvedOther)
                otherBase = genericTable.base
                otherArgs = genericTable.args
                contravariantParams = varianceFlags and TyVarianceFlags.WIDEN_TABLES != 0
            }
        } else if (resolvedOther is ITyClass) {
            otherBase = resolvedOther
            otherArgs = resolvedOther.getParams(context)
        }

        if (otherBase != null) {
            if (otherBase.equals(context, resolvedBase, TyEqualityFlags.fromVarianceFlags(varianceFlags))) {
                val baseArgCount = otherArgs?.size ?: 0
                return baseArgCount == 0 || args.size == otherArgs?.size && args.asSequence().zip(otherArgs.asSequence()).all { (arg, otherArg) ->
                    // Args are always invariant as we don't support use-site variance nor immutable/read-only annotations
                    arg.equals(context, otherArg, TyEqualityFlags.fromVarianceFlags(varianceFlags))
                            || (varianceFlags and TyVarianceFlags.STRICT_UNKNOWN == 0 && otherArg.isUnknown)
                            || (
                                (contravariantParams ||
                                    (arg is TyGenericParameter && context.abstractGenericScopeNames?.contains(arg.scopeName) == true)
                                )  && arg.contravariantOf(context, otherArg, varianceFlags)
                            )
                }
            }
        }

        return super.contravariantOf(context, resolvedOther, varianceFlags)
    }

    override fun accept(visitor: ITyVisitor) {
        visitor.visitGeneric(this)
    }

    override fun substitute(context: SearchContext, substitutor: ITySubstitutor): ITy {
        return substitutor.substitute(context, this)
    }

    override fun processMember(context: SearchContext, name: String, deep: Boolean, indexerSubstitutor: ITySubstitutor?, process: ProcessTypeMember): Boolean {
        return base.processMember(context, name, deep, TyChainSubstitutor.chain(getMemberSubstitutor(context), indexerSubstitutor), process)
    }

    override fun processIndexer(context: SearchContext, indexTy: ITy, exact: Boolean, deep: Boolean, indexerSubstitutor: ITySubstitutor?, process: ProcessTypeMember): Boolean {
        return base.processIndexer(context, indexTy, exact, deep, TyChainSubstitutor.chain(getMemberSubstitutor(context), indexerSubstitutor), process)
    }

    override fun isShape(context: SearchContext): Boolean {
        return base.isShape(context)
    }

    override fun guessMemberType(context: SearchContext, name: String): ITy? {
        if (base == Primitives.TABLE && args.size == 2 && (args[0] == Primitives.STRING
                        || args[0].contravariantOf(context, TyPrimitiveLiteral.getTy(TyPrimitiveKind.String, name), 0))) {
            return args[1]
        }

        return super<Ty>.guessMemberType(context, name)
    }

    override fun guessIndexerType(context: SearchContext, indexTy: ITy, exact: Boolean): ITy? {
        if (base == Primitives.TABLE && args.size == 2 && ((!exact && args[0].contravariantOf(context, indexTy, 0)) || indexTy == args[0])) {
            return args[1]
        }

        return super<Ty>.guessIndexerType(context, indexTy, exact)
    }

    override fun processMembers(context: SearchContext, deep: Boolean, process: ProcessTypeMember): Boolean {
        if (!base.processMembers(context, false) { _, classMember -> process(this, classMember) }) {
            return false
        }

        // super
        if (deep) {
            return Ty.processSuperClasses(context, this) {
                it.processMembers(context, false, process)
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
    override val psi: LuaDocGenericTableTy,
    val keyType: ITy = psi.keyType?.getType() ?: Primitives.UNKNOWN,
    val valueType: ITy = psi.valueType?.getType() ?: Primitives.UNKNOWN
) : TyGeneric(
        arrayOf(keyType, valueType),
        Primitives.TABLE
), IPsiTy<LuaDocGenericTableTy> {
    override fun processMember(context: SearchContext, name: String, deep: Boolean, indexerSubstitutor: ITySubstitutor?, process: ProcessTypeMember): Boolean {
        val indexerTy = if (indexerSubstitutor != null) {
            keyType.substitute(context, indexerSubstitutor)
        } else {
            keyType
        }

        Ty.eachResolved(context, indexerTy) {
            if ((it is ITyPrimitive && it.primitiveKind == TyPrimitiveKind.String)
                || (it is TyPrimitiveLiteral && it.primitiveKind == TyPrimitiveKind.String && it.value == name)) {
                return process(this, psi)
            }
        }

        return true
    }

    override fun processIndexer(context: SearchContext, indexTy: ITy, exact: Boolean, deep: Boolean, indexerSubstitutor: ITySubstitutor?, process: ProcessTypeMember): Boolean {
        val indexerTy = if (indexerSubstitutor != null) {
            keyType.substitute(context, indexerSubstitutor)
        } else {
            keyType
        }

        if (exact) {
            Ty.eachResolved(context, indexerTy) {
                if (it.equals(context, indexTy, 0)) {
                    return process(this, psi)
                }
            }
        } else if (indexerTy.contravariantOf(context, indexTy, TyVarianceFlags.STRICT_UNKNOWN)) {
            return process(this, psi)
        }

        return true
    }
}

