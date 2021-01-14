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

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.codeInsight.inspection.MatchFunctionSignatureInspection
import com.tang.intellij.lua.comment.psi.LuaDocClassRef
import com.tang.intellij.lua.ext.recursionGuard
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import java.lang.IllegalStateException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

enum class TyKind {
    Unknown,
    Primitive,
    Array,
    Function,
    Class,
    Alias,
    Union,
    Generic,
    Nil,
    Void,
    MultipleResults,
    GenericParam,
    PrimitiveLiteral,
    Snippet
}
enum class TyPrimitiveKind {
    String,
    Number,
    Boolean,
    Table,
    Function
}
class TyFlags {
    companion object {
        const val ANONYMOUS = 0x1
        const val GLOBAL = 0x2
        const val SELF_FUNCTION = 0x4 // xxx.method()
        const val ANONYMOUS_TABLE = 0x8 // local xx = {}, flag of this table `{}`
        const val SHAPE = 0x10 // variance is considered per field
        const val UNKNOWN = 0x20 // Unless STRICT_UNKNOWN is enabled, this type is covariant of all other types.
    }
}
class TyVarianceFlags {
    companion object {
        const val STRICT_UNKNOWN = 0x1 // When enabled UNKNOWN types are no longer treated as covariant of all types.
        const val ABSTRACT_PARAMS = 0x2 // A generic is to be considered contravariant if its TyParameter generic parameters are contravariant.
        const val WIDEN_TABLES = 0x4 // Generics (and arrays) are to be considered contravariant if their generic parameters (or base) are contravariant. Additionally, shapes are contravariant if their fields are contravariant.
        const val STRICT_NIL = 0x8 // In certain contexts nil is always strict, irrespective of the user's 'Strict nil checks' setting.
        const val NON_STRUCTURAL = 0x10 // Treat shapes as classes i.e. a shape is only covariant of another shape if it explicitly inherits from it.
    }
}

data class SignatureMatchResult(val signature: IFunSignature?, val substitutedSignature: IFunSignature?, val returnTy: ITy)

interface ITy : Comparable<ITy> {
    val kind: TyKind

    val displayName: String

    val flags: Int

    val booleanType: ITy

    fun equals(other: ITy, context: SearchContext): Boolean

    fun union(ty: ITy, context: SearchContext): ITy

    fun not(ty: ITy, context: SearchContext): ITy

    fun contravariantOf(other: ITy, context: SearchContext, flags: Int): Boolean

    fun covariantOf(other: ITy, context: SearchContext, flags: Int): Boolean

    fun getSuperClass(context: SearchContext): ITy?

    fun getParams(context: SearchContext): Array<TyGenericParameter>?

    fun visitSuper(searchContext: SearchContext, processor: Processor<ITyClass>)

    fun substitute(substitutor: ITySubstitutor): ITy

    fun eachTopClass(fn: Processor<ITy>)

    fun accept(visitor: ITyVisitor)

    fun acceptChildren(visitor: ITyVisitor)

    fun findMember(name: String, context: SearchContext): LuaClassMember? {
        var foundMember: LuaClassMember? = null

        processMember(context, name) { _, member ->
            foundMember = member
            false
        }

        return foundMember
    }

    fun findIndexer(indexTy: ITy, context: SearchContext, exact: Boolean = false): LuaClassMember?{
        var foundMember: LuaClassMember? = null

        processIndexer(context, indexTy, exact) { _, member ->
            foundMember = member
            false
        }

        return foundMember
    }

    fun findEffectiveMember(name: String, context: SearchContext): LuaClassMember? {
        var foundMember: LuaClassMember? = null

        processMember(context, name) { _, member ->
            if (member.isExplicitlyTyped) {
                foundMember = member
                return@processMember false
            } else if (foundMember == null) {
                foundMember = member
            }
            true
        }

        return foundMember
    }

    fun findEffectiveIndexer(indexTy: ITy, context: SearchContext, exact: Boolean = false): LuaClassMember? {
        var foundMember: LuaClassMember? = null

        processIndexer(context, indexTy, exact) { _, member ->
            if (member.isExplicitlyTyped) {
                foundMember = member
                return@processIndexer false
            } else if (foundMember == null) {
                foundMember = member
            }
            true
        }

        return foundMember
    }

    fun isShape(searchContext: SearchContext): Boolean {
        return flags and TyFlags.SHAPE != 0
    }

    fun guessMemberType(name: String, searchContext: SearchContext): ITy? {
        val member = findEffectiveMember(name, searchContext)

        if (member == null) {
            return if (isUnknown && LuaSettings.instance.isUnknownIndexable) {
                Ty.UNKNOWN
            } else {
                null
            }
        }

        return member.guessType(searchContext)?.let {
            val substitutor = getMemberSubstitutor(searchContext)
            return if (substitutor != null) it.substitute(substitutor) else it
        } ?: Ty.UNKNOWN
    }

    fun guessIndexerType(indexTy: ITy, searchContext: SearchContext, exact: Boolean = false): ITy? {
        var ty: ITy? = null
        val substitutor: Lazy<ITySubstitutor?> = lazy {
            getMemberSubstitutor(searchContext)
        }

        Ty.eachResolved(indexTy, searchContext) { resolvedIndexTy ->
            val member = findEffectiveIndexer(resolvedIndexTy, searchContext, exact)

            if (member == null) {
                if (isUnknown && LuaSettings.instance.isUnknownIndexable) {
                    return Ty.UNKNOWN
                } else {
                    return@eachResolved
                }
            }

            val memberTy = member.guessType(searchContext)?.let {
                substitutor.value?.let { substitutor ->
                    it.substitute(substitutor)
                } ?: it
            }

            if (memberTy == null) {
                return Ty.UNKNOWN
            }

            ty = TyUnion.union(ty, memberTy, searchContext)
        }

        return ty
    }

    fun getMemberSubstitutor(context: SearchContext): ITySubstitutor? {
        return getSuperClass(context)?.getMemberSubstitutor(context)
    }

    fun processMember(context: SearchContext, name: String, deep: Boolean = true, process: (ITy, LuaClassMember) -> Boolean): Boolean

    fun processIndexer(context: SearchContext, indexTy: ITy, exact: Boolean = false, deep: Boolean = true, process: (ITy, LuaClassMember) -> Boolean): Boolean

    fun processMembers(context: SearchContext, deep: Boolean = true, process: (ITy, LuaClassMember) -> Boolean): Boolean

    fun processSignatures(context: SearchContext, processor: Processor<IFunSignature>): Boolean
}

fun ITy.hasFlag(flag: Int): Boolean = flags and flag == flag

val ITy.isGlobal: Boolean
    get() = hasFlag(TyFlags.GLOBAL)

val ITy.isAnonymous: Boolean
    get() = hasFlag(TyFlags.ANONYMOUS)

val ITy.isAnonymousTable: Boolean
    get() = hasFlag(TyFlags.ANONYMOUS_TABLE)

val ITy.isUnknown: Boolean
    get() = hasFlag(TyFlags.UNKNOWN)

val ITy.isColonCall: Boolean
    get() = hasFlag(TyFlags.SELF_FUNCTION)

fun ITy.findCandidateSignatures(context: SearchContext, nArgs: Int): Collection<IFunSignature> {
    val candidates = mutableListOf<IFunSignature>()
    var lastCandidate: IFunSignature? = null
    processSignatures(context) {
        val params = it.params
        if (params == null || params.size >= nArgs || it.variadicParamTy != null) {
            candidates.add(it)
        }
        lastCandidate = it
        true
    }
    if (candidates.size == 0) {
        lastCandidate?.let { candidates.add(it) }
    }
    return candidates
}

fun ITy.findCandidateSignatures(context: SearchContext, call: LuaCallExpr): Collection<IFunSignature> {
    val n = call.argList.size
    // 是否是 inst:method() 被用为 inst.method(self) 形式
    val isInstanceMethodUsedAsStaticMethod = isColonCall && call.isMethodDotCall
    if (isInstanceMethodUsedAsStaticMethod)
        return findCandidateSignatures(context, n - 1)
    val isStaticMethodUsedAsInstanceMethod = !isColonCall && call.isMethodColonCall
    return findCandidateSignatures(context, if (isStaticMethodUsedAsInstanceMethod) n + 1 else n)
}

fun ITy.matchSignature(context: SearchContext, call: LuaCallExpr, processProblem: ProcessProblem? = null): SignatureMatchResult? {
    val args = call.argList
    val concreteArgTypes = mutableListOf<MatchFunctionSignatureInspection.ConcreteTypeInfo>()
    var multipleResultsVariadicTypeInfo: MatchFunctionSignatureInspection.ConcreteTypeInfo? = null

    args.forEachIndexed { index, luaExpr ->
        // TODO: unknown vs. any - should be unknown
        val ty = (
                if (index == args.lastIndex)
                    context.withMultipleResults { luaExpr.guessType(context) }
                else
                    context.withIndex(0) { luaExpr.guessType(context) }
                ) ?: Ty.UNKNOWN

        if (ty is TyMultipleResults && index == args.lastIndex) {
            val concreteResults = if (ty.variadic) {
                multipleResultsVariadicTypeInfo = MatchFunctionSignatureInspection.ConcreteTypeInfo(luaExpr, ty.list.last())
                ty.list.dropLast(1)
            } else {
                ty.list
            }

            concreteArgTypes.addAll(concreteResults.map { MatchFunctionSignatureInspection.ConcreteTypeInfo(luaExpr, it) })
        } else {
            concreteArgTypes.add(MatchFunctionSignatureInspection.ConcreteTypeInfo(luaExpr, TyMultipleResults.getResult(context, ty, 0)))
        }
    }

    val variadicArg: MatchFunctionSignatureInspection.ConcreteTypeInfo? = multipleResultsVariadicTypeInfo
    val problems = if (processProblem != null) mutableMapOf<IFunSignature, Collection<Problem>>() else null
    val candidates = findCandidateSignatures(context, call)
    var fallbackReturnTy: ITy? = null

    candidates.forEach {
        var parameterCount = 0
        var candidateFailed = false
        val signatureProblems = if (problems != null) mutableListOf<Problem>() else null

        val substitutor = call.createSubstitutor(it, context)
        val signature = it.substitute(substitutor)

        signature.processParameters(call) { i, pi ->
            parameterCount = i + 1
            val typeInfo = concreteArgTypes.getOrNull(i) ?: variadicArg

            if (typeInfo == null || typeInfo == variadicArg) {
                var problemElement = call.lastChild.lastChild

                // Some PSI elements injected by IntelliJ (e.g. PsiErrorElementImpl) can be empty and thus cannot be targeted for our own errors.
                while (problemElement != null && problemElement.textLength == 0) {
                    problemElement = problemElement.prevSibling
                }

                problemElement = problemElement ?: call.lastChild

                candidateFailed = true

                if (!call.isMethodColonCall && i == 0 && pi.name == Constants.WORD_SELF) {
                    signatureProblems?.add(Problem(null, problemElement, "Missing self argument.\n\nDid you mean to call the method with a colon?"))
                } else {
                    signatureProblems?.add(Problem(null, problemElement, "Missing argument: ${pi.name}: ${pi.ty}"))
                }

                if (typeInfo == null) {
                    return@processParameters true
                }
            }

            val paramType = pi.ty ?: Ty.UNKNOWN
            val argType = typeInfo.ty
            val argExpr = args.getOrNull(i) ?: args.last()
            val varianceFlags = if (argExpr is LuaTableExpr) {
                TyVarianceFlags.WIDEN_TABLES
            } else 0

            if (processProblem != null) {
                val contravariant = ProblemUtil.contravariantOf(paramType, argType, context, varianceFlags, null, argExpr) { problem ->
                    var contextualMessage = if (i >= args.size &&
                        (concreteArgTypes.size > args.size || (variadicArg != null && concreteArgTypes.size >= args.size))
                    ) {
                        "Result ${i + 1}, ${problem.message.decapitalize()}"
                    } else {
                        problem.message
                    }

                    if (!call.isMethodColonCall && i == 0 && pi.name == Constants.WORD_SELF) {
                        contextualMessage += ".\n\nDid you mean to call the method with a colon?"
                    }

                    signatureProblems?.add(Problem(null, problem.sourceElement, contextualMessage, problem.highlightType))
                }

                if (!contravariant) {
                    candidateFailed = true
                }
            } else if (!paramType.contravariantOf(argType, context, varianceFlags)) {
                candidateFailed = true
            }

            true
        }

        val varargParamTy = signature.variadicParamTy

        if (parameterCount < concreteArgTypes.size) {
            if (varargParamTy != null) {
                for (i in parameterCount until args.size) {
                    val argType = concreteArgTypes.getOrNull(i)?.ty?.let {
                        if (it is TyMultipleResults) it.list.first() else it
                    } ?: variadicArg!!.ty
                    val argExpr = args.get(i)
                    val varianceFlags = if (argExpr is LuaTableExpr) TyVarianceFlags.WIDEN_TABLES else 0

                    if (processProblem != null) {
                        val contravariant = ProblemUtil.contravariantOf(varargParamTy, argType, context, varianceFlags, null, argExpr) { problem ->
                            signatureProblems?.add(Problem(null, problem.sourceElement, problem.message, problem.highlightType))
                        }

                        if (!contravariant) {
                            candidateFailed = true
                        }
                    } else if (!varargParamTy.contravariantOf(argType, context, varianceFlags)) {
                        candidateFailed = true
                    }
                }
            } else {
                if (parameterCount < args.size) {
                    for (i in parameterCount until args.size) {
                        candidateFailed = true
                        signatureProblems?.add(Problem(null, args[i], "Too many arguments."))
                    }
                } else {
                    // Last argument is TyMultipleResults, just a weak warning.
                    val excess = parameterCount - args.size
                    val message = if (excess == 1) "1 result is an excess argument." else "${excess} results are excess arguments."
                    signatureProblems?.add(Problem(null, args.last(), message, ProblemHighlightType.WEAK_WARNING))
                }
            }
        } else if (varargParamTy != null && variadicArg != null) {
            if (processProblem != null) {
                val contravariant = ProblemUtil.contravariantOf(varargParamTy, variadicArg.ty, context, 0, null, variadicArg.param) { problem ->
                    val contextualMessage = "Variadic result, ${problem.message.decapitalize()}"
                    signatureProblems?.add(Problem(null, problem.sourceElement, contextualMessage, problem.highlightType))
                }

                if (!contravariant) {
                    candidateFailed = true
                }
            } else if (!varargParamTy.contravariantOf(variadicArg.ty, context, 0)) {
                candidateFailed = true
            }
        }

        if (!candidateFailed) {
            if (processProblem != null) {
                signatureProblems?.forEach(processProblem)
            }

            return SignatureMatchResult(it, signature, signature.returnTy ?: TyMultipleResults(listOf(Ty.UNKNOWN), true))
        }

        if (fallbackReturnTy == null && signature.returnTy != Ty.VOID) {
            fallbackReturnTy = signature.returnTy
        }

        if (signatureProblems != null) {
            problems?.put(it, signatureProblems)
        }
    }

    if (processProblem != null) {
        val multipleCandidates = candidates.size > 1

        problems?.forEach { signature, signatureProblems ->
            signatureProblems.forEach {
                it.message = if (multipleCandidates) "${it.message}. In: ${signature.displayName}\n" else it.message
                processProblem(it)
            }
        }
    }

    if (fallbackReturnTy == null) {
        fallbackReturnTy = if (candidates.size > 0) {
            Ty.VOID
        } else if (this is ITyFunction) {
            val substitutor = call.createSubstitutor(this.mainSignature, context)
            this.mainSignature.substitute(substitutor).returnTy ?: TyMultipleResults(listOf(Ty.UNKNOWN), true)
        } else {
            var fallbackSignature: IFunSignature? = null

            processSignatures(context) {
                fallbackSignature = it
                false
            }

            fallbackSignature?.let { it.returnTy ?: TyMultipleResults(listOf(Ty.UNKNOWN), true) }
        }
    }

    if (fallbackReturnTy == null) {
        // Not callable
        return null
    }

    // Callable, but no matching signature
    return SignatureMatchResult(null, null, fallbackReturnTy!!)
}

fun ITy.matchSignature(context: SearchContext, call: LuaCallExpr, problemsHolder: ProblemsHolder): SignatureMatchResult? {
    return matchSignature(context, call) { problem ->
        problemsHolder.registerProblem(problem.sourceElement, problem.message, problem.highlightType ?: ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
    }
}

abstract class Ty(override val kind: TyKind) : ITy {

    final override var flags: Int = 0

    override val displayName: String
        get() = TyRenderer.SIMPLE.render(this)

    // Lazy initialization because Ty.TRUE is itself a Ty that needs to be instantiated and refers to itself.
    override val booleanType: ITy by lazy { TRUE }

    fun addFlag(flag: Int) {
        flags = flags or flag
    }

    override fun accept(visitor: ITyVisitor) {
        visitor.visitTy(this)
    }

    override fun acceptChildren(visitor: ITyVisitor) {
    }

    override fun union(ty: ITy, context: SearchContext): ITy {
        return TyUnion.union(this, ty, context)
    }

    override fun not(ty: ITy, context: SearchContext): ITy {
        val resolved = (this as? ITyResolvable)?.resolve(context) ?: this

        if (resolved !== this) {
            val result = resolved.not(ty, context)
            return if (result === resolved) this else result
        }

        if (ty.contravariantOf(this, context, TyVarianceFlags.STRICT_NIL or TyVarianceFlags.STRICT_UNKNOWN or TyVarianceFlags.NON_STRUCTURAL)) {
            return Ty.VOID
        }

        return this
    }

    override fun toString(): String {
        val list = mutableListOf<String>()
        TyUnion.each(this) { //尽量不使用Global
            if (!it.isAnonymous && !(it is ITyClass && it.isGlobal)) {
                if (it is ITyFunction || it is TyMultipleResults) {
                    list.add("(${it.displayName})")
                } else {
                    list.add(it.displayName)
                }
            }
        }
        if (list.isEmpty()) { //使用Global
            TyUnion.each(this) {
                if (!it.isAnonymous && (it is ITyClass && it.isGlobal)) {
                    if (it is ITyFunction || it is TyMultipleResults) {
                        list.add("(${it.displayName})")
                    } else {
                        list.add(it.displayName)
                    }
                }
            }
        }
        return list.joinToString(" | ")
    }

    override fun contravariantOf(other: ITy, context: SearchContext, flags: Int): Boolean {
        if ((other.kind == TyKind.Unknown && flags and TyVarianceFlags.STRICT_UNKNOWN == 0)
            || (other.kind == TyKind.Nil && flags and TyVarianceFlags.STRICT_NIL == 0 && !LuaSettings.instance.isNilStrict)
        ) {
            return true
        }

        val resolvedOther = resolve(other, context)

        if (this.equals(resolvedOther, context)) {
            return true
        }

        if (resolvedOther != other) {
            return contravariantOf(resolvedOther, context, flags)
        }

        if (resolvedOther is TyUnion) {
            TyUnion.each(resolvedOther) {
                if (it !is TySnippet && !contravariantOf(it, context, flags)) {
                    return false
                }
            }

            return true
        }

        if ((flags and TyVarianceFlags.NON_STRUCTURAL == 0 || other.isAnonymousTable) && isShape(context)) {
            val isCovariant: Boolean? = recursionGuard(resolvedOther, {
                ProblemUtil.contravariantOfShape(this, resolvedOther, context, flags)
            })

            if (isCovariant != null) {
                return isCovariant
            }
        }

        val otherSuper = other.getSuperClass(context)
        return otherSuper != null && contravariantOf(otherSuper, context, flags)
    }

    override fun covariantOf(other: ITy, context: SearchContext, flags: Int): Boolean {
        return other.contravariantOf(this, context, flags)
    }

    override fun getSuperClass(context: SearchContext): ITy? {
        return null
    }

    override fun getParams(context: SearchContext): Array<TyGenericParameter>? {
        return null
    }

    override fun visitSuper(searchContext: SearchContext, processor: Processor<ITyClass>) {
        val superType = getSuperClass(searchContext) as? ITyClass ?: return
        if (processor.process(superType))
            superType.visitSuper(searchContext, processor)
    }

    override fun compareTo(other: ITy): Int {
        return other.displayName.compareTo(displayName)
    }

    override fun substitute(substitutor: ITySubstitutor): ITy {
        return substitutor.substitute(this)
    }

    override fun eachTopClass(fn: Processor<ITy>) {
        when (this) {
            is ITyClass -> fn.process(this)
            is ITyGeneric -> fn.process(this)
            is TyUnion -> {
                ContainerUtil.process(getChildTypes()) {
                    if (it is ITyClass && !fn.process(it))
                        return@process false
                    true
                }
            }
            is TyMultipleResults -> {
                list.firstOrNull()?.eachTopClass(fn)
            }
        }
    }

    override fun processMember(context: SearchContext, name: String, deep: Boolean, process: (ITy, LuaClassMember) -> Boolean): Boolean {
        return true
    }

    override fun processIndexer(context: SearchContext, indexTy: ITy, exact: Boolean, deep: Boolean, process: (ITy, LuaClassMember) -> Boolean): Boolean {
        return true
    }

    override fun processMembers(context: SearchContext, deep: Boolean, process: (ITy, LuaClassMember) -> Boolean): Boolean {
        return true
    }

    override fun processSignatures(context: SearchContext, processor: Processor<IFunSignature>): Boolean {
        return true
    }

    companion object {

        // Defined first because each Ty implements booleanType, which returns a reference to these constants.
        val TRUE = TyPrimitiveLiteral.getTy(TyPrimitiveKind.Boolean, Constants.WORD_TRUE)
        val FALSE = TyPrimitiveLiteral.getTy(TyPrimitiveKind.Boolean, Constants.WORD_FALSE)

        val UNKNOWN = TyUnknown()
        val VOID = TyVoid()
        val NIL = TyNil()

        val BOOLEAN = TyPrimitive(TyPrimitiveKind.Boolean, Constants.WORD_BOOLEAN)
        val FUNCTION = TyPrimitive(TyPrimitiveKind.Function, Constants.WORD_FUNCTION)
        val NUMBER = TyPrimitive(TyPrimitiveKind.Number, Constants.WORD_NUMBER)
        val STRING = TyPrimitiveClass(TyPrimitiveKind.String, Constants.WORD_STRING)
        val TABLE = TyPrimitive(TyPrimitiveKind.Table, Constants.WORD_TABLE)

        private val serializerMap = mapOf<TyKind, ITySerializer>(
                TyKind.Array to TyArraySerializer,
                TyKind.Class to TyClassSerializer,
                TyKind.Alias to TyAliasSerializer,
                TyKind.Function to TyFunctionSerializer,
                TyKind.Generic to TyGenericSerializer,
                TyKind.GenericParam to TyGenericParamSerializer,
                TyKind.Primitive to TyPrimitiveSerializer,
                TyKind.PrimitiveLiteral to TyPrimitiveLiteralSerializer,
                TyKind.Snippet to TySnippetSerializer,
                TyKind.MultipleResults to TyMultipleResultsSerializer,
                TyKind.Union to TyUnionSerializer
        )

        private fun getKind(ordinal: Int): TyKind {
            return TyKind.values().firstOrNull { ordinal == it.ordinal } ?: TyKind.Unknown
        }

        fun getBuiltin(name: String): ITy? {
            return when (name) {
                Constants.WORD_NIL -> NIL
                Constants.WORD_VOID -> VOID
                Constants.WORD_ANY -> UNKNOWN
                Constants.WORD_BOOLEAN -> BOOLEAN
                Constants.WORD_TRUE -> TRUE
                Constants.WORD_FALSE -> FALSE
                Constants.WORD_STRING -> STRING
                Constants.WORD_NUMBER -> NUMBER
                Constants.WORD_TABLE -> TABLE
                Constants.WORD_FUNCTION -> FUNCTION
                else -> null
            }
        }

        fun create(name: String, psiElement: PsiElement? = null): ITy {
            return getBuiltin(name) ?: TyLazyClass(name, psiElement)
        }

        fun create(classRef: LuaDocClassRef): ITy {
            val simpleType = create(classRef.typeRef.name, classRef)
            return if (classRef.tyList.size > 0) {
                TyGeneric(classRef.tyList.map { it.getType() }.toTypedArray(), simpleType)
            } else simpleType
        }

        @ExperimentalContracts
        fun isInvalid(ty: ITy?): Boolean {
            contract {
                returns(true) implies (ty == null || ty is TyVoid)
                returns(false) implies (ty != null && ty !is TyVoid)
            }
            return ty == null || ty is TyVoid
        }

        private fun getSerializer(kind: TyKind): ITySerializer? {
            return serializerMap[kind]
        }

        fun serialize(ty: ITy, stream: StubOutputStream) {
            stream.writeByte(ty.kind.ordinal)
            stream.writeInt(ty.flags)
            val serializer = getSerializer(ty.kind)
            serializer?.serialize(ty, stream)
        }

        fun deserialize(stream: StubInputStream): ITy {
            val kind = getKind(stream.readByte().toInt())
            val flags = stream.readInt()
            return when (kind) {
                TyKind.Nil -> NIL
                TyKind.Unknown -> UNKNOWN
                TyKind.Void -> VOID
                else -> {
                    val serializer = getSerializer(kind)

                    if (serializer == null) {
                        throw IllegalStateException("Cannot deserialize unknown type of kind ${kind}")
                    }

                    serializer.deserialize(flags, stream)
                }
            }
        }

        fun processSuperClasses(start: ITy, searchContext: SearchContext, processor: (ITy) -> Boolean): Boolean {
            val processedName = mutableSetOf<String>()
            var cur: ITy? = start

            while (cur != null) {
                ProgressManager.checkCanceled()

                val superType = cur.getSuperClass(searchContext)

                if (superType != null) {
                    if (!processedName.add(superType.displayName)) {
                        return true
                    }
                    if (!processor(superType))
                        return false
                }

                cur = superType
            }

            return true
        }

        inline fun eachResolved(ty: ITy, context: SearchContext, fn: (ITy) -> Unit) {
            if (!TyUnion.any(ty) { it is ITyResolvable && it.willResolve(context) }) {
                TyUnion.each(ty, fn)
                return
            }

            val visitedTys = mutableSetOf<ITy>()

            val pendingTys = if (ty is TyUnion) {
                visitedTys.add(ty)
                mutableListOf<ITy>().apply { addAll(ty.getChildTypes()) }
            } else {
                mutableListOf(ty)
            }

            while (pendingTys.isNotEmpty()) {
                val pendingTy = pendingTys.removeLast()

                if (visitedTys.add(pendingTy)) {
                    val resolvedMemberTy = (pendingTy as? ITyResolvable)?.resolve(context) ?: pendingTy

                    if (resolvedMemberTy !== pendingTy) {
                        if (resolvedMemberTy is TyUnion) {
                            pendingTys.addAll(resolvedMemberTy.getChildTypes())
                        } else {
                            pendingTys.add(resolvedMemberTy)
                        }
                    } else {
                        fn(pendingTy)
                    }
                }
            }
        }

        fun resolve(ty: ITy, context: SearchContext): ITy {
            if (!TyUnion.any(ty) { it is ITyResolvable && it.willResolve(context) }) {
                return ty
            }

            val tyCount = if (ty is TyUnion) ty.size else 1
            val memberTys = ArrayList<ITy>(maxOf(10, 3 * tyCount))

            eachResolved(ty, context) {
                memberTys.add(it)
            }

            if (tyCount == memberTys.size) {
                val unresolved = if (ty is TyUnion) {
                    val unionSet = ty.getChildTypes()
                    memberTys.all { unionSet.contains(it) }
                } else {
                    memberTys.first() === ty
                }

                if (unresolved) {
                    return ty
                }
            }

            return if (memberTys.size == 1) {
                memberTys.first()
            } else {
                TyUnion.union(memberTys, context)
            }
        }
    }
}

class TyUnknown : Ty(TyKind.Unknown) {

    init {
        this.flags = this.flags or TyFlags.UNKNOWN
    }

    override fun equals(other: Any?): Boolean {
        return other is TyUnknown
    }

    override fun equals(other: ITy, context: SearchContext): Boolean {
        if (other === UNKNOWN) {
            return true
        }

        return resolve(other, context) is TyUnknown
    }

    override fun hashCode(): Int {
        return Constants.WORD_ANY.hashCode()
    }

    override fun contravariantOf(other: ITy, context: SearchContext, flags: Int): Boolean {
        return other !is TyMultipleResults
    }

    override fun guessMemberType(name: String, searchContext: SearchContext): ITy? {
        return if (LuaSettings.instance.isUnknownIndexable) UNKNOWN else null
    }

    override fun guessIndexerType(indexTy: ITy, searchContext: SearchContext, exact: Boolean): ITy? {
        return if (LuaSettings.instance.isUnknownIndexable) UNKNOWN else null
    }
}

class TyNil : Ty(TyKind.Nil) {

    override val booleanType = FALSE

    override fun equals(other: ITy, context: SearchContext): Boolean {
        if (other === NIL) {
            return true
        }

        return resolve(other, context) is TyNil
    }

    override fun contravariantOf(other: ITy, context: SearchContext, flags: Int): Boolean {
        return other.kind == TyKind.Nil || super.contravariantOf(other, context, flags)
    }
}

class TyVoid : Ty(TyKind.Void) {

    override fun equals(other: ITy, context: SearchContext): Boolean {
        if (other === VOID) {
            return true
        }

        return resolve(other, context) is TyVoid
    }

    override fun contravariantOf(other: ITy, context: SearchContext, flags: Int): Boolean {
        return other.kind == TyKind.Void || super.contravariantOf(other, context, flags)
    }
}
