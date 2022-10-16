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

typealias ProcessTypeMember = (ownerTy: ITy, member: TypeMember) -> Boolean

interface ITy : Comparable<ITy> {
    val kind: TyKind

    val displayName: String

    val flags: Int

    val booleanType: ITy

    fun equals(context: SearchContext, other: ITy): Boolean

    fun union(context: SearchContext, ty: ITy): ITy

    fun not(context: SearchContext, ty: ITy): ITy

    fun contravariantOf(context: SearchContext, other: ITy, flags: Int): Boolean

    fun covariantOf(context: SearchContext, other: ITy, flags: Int): Boolean

    fun getSuperType(context: SearchContext): ITy?

    fun getParams(context: SearchContext): Array<TyGenericParameter>?

    fun visitSuper(context: SearchContext, processor: Processor<ITyClass>)

    fun substitute(context: SearchContext, substitutor: ITySubstitutor): ITy

    fun eachTopClass(fn: Processor<ITy>)

    fun accept(visitor: ITyVisitor)

    fun acceptChildren(visitor: ITyVisitor)

    fun findMember(context: SearchContext, name: String): TypeMember? {
        var foundMember: TypeMember? = null

        processMember(context, name) { _, member ->
            foundMember = member
            false
        }

        return foundMember
    }

    fun findIndexer(context: SearchContext, indexTy: ITy, exact: Boolean = false): TypeMember?{
        var foundMember: TypeMember? = null

        processIndexer(context, indexTy, exact) { _, member ->
            foundMember = member
            false
        }

        return foundMember
    }

    fun findEffectiveMember(context: SearchContext, name: String): TypeMember? {
        var foundMember: TypeMember? = null

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

    fun findEffectiveIndexer(context: SearchContext, indexTy: ITy, exact: Boolean = false): TypeMember? {
        var foundMember: TypeMember? = null

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

    fun isShape(context: SearchContext): Boolean {
        return flags and TyFlags.SHAPE != 0
    }

    fun guessMemberType(searchContext: SearchContext, name: String): ITy? {
        val context = if (isAnonymous) searchContext else searchContext.getProjectContext()
        val member = findEffectiveMember(context, name)

        if (member == null) {
            return if (isUnknown && LuaSettings.instance.isUnknownIndexable) {
                Primitives.UNKNOWN
            } else {
                null
            }
        }

        return member.guessType(context)?.let {
            val substitutor = getMemberSubstitutor(context)
            return if (substitutor != null) it.substitute(context, substitutor) else it
        } ?: Primitives.UNKNOWN
    }

    fun guessIndexerType(searchContext: SearchContext, indexTy: ITy, exact: Boolean = false): ITy? {
        val context = searchContext.getProjectContext()
        var ty: ITy? = null
        val substitutor: Lazy<ITySubstitutor?> = lazy {
            getMemberSubstitutor(context)
        }

        Ty.eachResolved(context, indexTy) { resolvedIndexTy ->
            val member = findEffectiveIndexer(context, resolvedIndexTy, exact)

            if (member == null) {
                if (isUnknown && LuaSettings.instance.isUnknownIndexable) {
                    return Primitives.UNKNOWN
                } else {
                    return@eachResolved
                }
            }

            val memberTy = member.guessType(context)?.let {
                substitutor.value?.let { substitutor ->
                    it.substitute(context, substitutor)
                } ?: it
            }

            if (memberTy == null) {
                return Primitives.UNKNOWN
            }

            ty = TyUnion.union(context, ty, memberTy)
        }

        return ty
    }

    fun getMemberSubstitutor(context: SearchContext): ITySubstitutor? {
        return getSuperType(context)?.let {
            Ty.resolve(context, it).getMemberSubstitutor(context)
        }
    }

    fun processMember(context: SearchContext, name: String, deep: Boolean = true, process: ProcessTypeMember): Boolean

    fun processIndexer(context: SearchContext, indexTy: ITy, exact: Boolean = false, deep: Boolean = true, process: ProcessTypeMember): Boolean

    fun processMembers(context: SearchContext, deep: Boolean = true, process: ProcessTypeMember): Boolean

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
        val ty = context.withListEntry(index == args.lastIndex) {
            luaExpr.guessType(context)
        }  ?: Primitives.UNKNOWN

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

        val substitutor = call.createSubstitutor(context, it)
        val signature = it.substitute(context, substitutor)

        if (signature.params != null) {
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

                val paramType = pi.ty ?: Primitives.UNKNOWN
                val argType = typeInfo.ty
                val argExpr = args.getOrNull(i) ?: args.last()
                val varianceFlags = if (argExpr is LuaTableExpr) {
                    TyVarianceFlags.WIDEN_TABLES
                } else 0

                if (processProblem != null) {
                    val contravariant = ProblemUtil.contravariantOf(context, paramType, argType, varianceFlags, null, argExpr) { problem ->
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
                } else if (!paramType.contravariantOf(context, argType, varianceFlags)) {
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
                            val contravariant = ProblemUtil.contravariantOf(context, varargParamTy, argType, varianceFlags, null, argExpr) { problem ->
                                signatureProblems?.add(Problem(null, problem.sourceElement, problem.message, problem.highlightType))
                            }

                            if (!contravariant) {
                                candidateFailed = true
                            }
                        } else if (!varargParamTy.contravariantOf(context, argType, varianceFlags)) {
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
                    val contravariant = ProblemUtil.contravariantOf(context, varargParamTy, variadicArg.ty, 0, null, variadicArg.param) { problem ->
                        val contextualMessage = "Variadic result, ${problem.message.decapitalize()}"
                        signatureProblems?.add(Problem(null, problem.sourceElement, contextualMessage, problem.highlightType))
                    }

                    if (!contravariant) {
                        candidateFailed = true
                    }
                } else if (!varargParamTy.contravariantOf(context, variadicArg.ty, 0)) {
                    candidateFailed = true
                }
            }
        }

        if (!candidateFailed) {
            if (processProblem != null) {
                signatureProblems?.forEach(processProblem)
            }

            return SignatureMatchResult(it, signature, signature.returnTy ?: TyMultipleResults(listOf(Primitives.UNKNOWN), true))
        }

        if (fallbackReturnTy == null && signature.returnTy != Primitives.VOID) {
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
            Primitives.VOID
        } else if (this is ITyFunction) {
            val substitutor = call.createSubstitutor(context, mainSignature)
            mainSignature.substitute(context, substitutor).returnTy ?: TyMultipleResults(listOf(Primitives.UNKNOWN), true)
        } else {
            var fallbackSignature: IFunSignature? = null

            processSignatures(context) {
                fallbackSignature = it
                false
            }

            fallbackSignature?.let { it.returnTy ?: TyMultipleResults(listOf(Primitives.UNKNOWN), true) }
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

    // Lazy initialization because Primitives.TRUE is itself a Ty that needs to be instantiated and refers to itself.
    override val booleanType: ITy by lazy { Primitives.TRUE }

    fun addFlag(flag: Int) {
        flags = flags or flag
    }

    override fun accept(visitor: ITyVisitor) {
        visitor.visitTy(this)
    }

    override fun acceptChildren(visitor: ITyVisitor) {
    }

    override fun union(context: SearchContext, ty: ITy): ITy {
        return TyUnion.union(context, this, ty)
    }

    override fun not(context: SearchContext, ty: ITy): ITy {
        val resolved = (this as? ITyResolvable)?.resolve(context) ?: this

        if (resolved !== this) {
            val result = resolved.not(context, ty)
            return if (result === resolved) this else result
        }

        if (ty.contravariantOf(context, this, TyVarianceFlags.STRICT_NIL or TyVarianceFlags.STRICT_UNKNOWN or TyVarianceFlags.NON_STRUCTURAL)) {
            return Primitives.VOID
        }

        return this
    }

    override fun toString() = displayName

    override fun contravariantOf(context: SearchContext, other: ITy, flags: Int): Boolean {
        if ((other.kind == TyKind.Unknown && flags and TyVarianceFlags.STRICT_UNKNOWN == 0)
            || (other.kind == TyKind.Nil && flags and TyVarianceFlags.STRICT_NIL == 0 && !LuaSettings.instance.isNilStrict)
        ) {
            return true
        }

        val resolvedOther = resolve(context, other)

        if (this.equals(context, resolvedOther)) {
            return true
        }

        if (resolvedOther != other) {
            return contravariantOf(context, resolvedOther, flags)
        }

        if (resolvedOther is TyUnion) {
            TyUnion.each(resolvedOther) {
                if (it !is TySnippet && !contravariantOf(context, it, flags)) {
                    return false
                }
            }

            return true
        }

        if ((flags and TyVarianceFlags.NON_STRUCTURAL == 0 || other.isAnonymousTable) && isShape(context)) {
            val isCovariant: Boolean? = recursionGuard(resolvedOther, {
                ProblemUtil.contravariantOfShape(context, this, resolvedOther, flags)
            })

            if (isCovariant != null) {
                return isCovariant
            }
        }

        val otherSuper = other.getSuperType(context)
        return otherSuper != null && contravariantOf(context, otherSuper, flags)
    }

    override fun covariantOf(context: SearchContext, other: ITy, flags: Int): Boolean {
        return other.contravariantOf(context, this, flags)
    }

    override fun getSuperType(context: SearchContext): ITy? {
        return null
    }

    override fun getParams(context: SearchContext): Array<TyGenericParameter>? {
        return null
    }

    override fun visitSuper(context: SearchContext, processor: Processor<ITyClass>) {
        val superType = getSuperType(context) as? ITyClass ?: return
        if (processor.process(superType))
            superType.visitSuper(context, processor)
    }

    override fun compareTo(other: ITy): Int {
        return other.displayName.compareTo(displayName)
    }

    override fun substitute(context: SearchContext, substitutor: ITySubstitutor): ITy {
        return substitutor.substitute(context, this)
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

    override fun processMember(context: SearchContext, name: String, deep: Boolean, process: ProcessTypeMember): Boolean {
        return true
    }

    override fun processIndexer(context: SearchContext, indexTy: ITy, exact: Boolean, deep: Boolean, process: ProcessTypeMember): Boolean {
        return true
    }

    override fun processMembers(context: SearchContext, deep: Boolean, process: ProcessTypeMember): Boolean {
        return true
    }

    override fun processSignatures(context: SearchContext, processor: Processor<IFunSignature>): Boolean {
        return true
    }

    companion object {

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
                Constants.WORD_NIL -> Primitives.NIL
                Constants.WORD_VOID -> Primitives.VOID
                Constants.WORD_ANY -> Primitives.UNKNOWN
                Constants.WORD_BOOLEAN -> Primitives.BOOLEAN
                Constants.WORD_TRUE -> Primitives.TRUE
                Constants.WORD_FALSE -> Primitives.FALSE
                Constants.WORD_STRING -> Primitives.STRING
                Constants.WORD_NUMBER -> Primitives.NUMBER
                Constants.WORD_TABLE -> Primitives.TABLE
                Constants.WORD_FUNCTION -> Primitives.FUNCTION
                else -> null
            }
        }

        fun create(name: String, psiElement: PsiElement? = null): ITy {
            return getBuiltin(name) ?: TyLazyClass(name, psiElement)
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
                TyKind.Nil -> Primitives.NIL
                TyKind.Unknown -> Primitives.UNKNOWN
                TyKind.Void -> Primitives.VOID
                else -> {
                    val serializer = getSerializer(kind)

                    if (serializer == null) {
                        throw IllegalStateException("Cannot deserialize unknown type of kind ${kind}")
                    }

                    serializer.deserialize(flags, stream)
                }
            }
        }

        fun processSuperClasses(context: SearchContext, start: ITy, processor: (ITy) -> Boolean): Boolean {
            val processedName = mutableSetOf<String>()
            var cur: ITy? = start

            while (cur != null) {
                ProgressManager.checkCanceled()

                if (!processedName.add(cur.displayName)) {
                    return true
                }

                val superTy = cur.getSuperType(context)?.let {
                    Ty.resolve(context, it)
                }

                if (superTy != null) {
                    if (!processor(superTy))
                        return false
                }

                cur = superTy
            }

            return true
        }

        data class PendingTy(val ty: ITy, val unresolvedTy: ITy)

        inline fun eachUnresolved(context: SearchContext, ty: ITy, fn: (unresolvedTy: ITy, resolvedTy: ITy) -> Unit) {
            if (!TyUnion.any(ty) { it is ITyResolvable && it.willResolve(context) }) {
                TyUnion.each(ty) {
                    fn(it, it)
                }
                return
            }

            val visitedTys = mutableSetOf<ITy>()

            val pendingTys = if (ty is TyUnion) {
                visitedTys.add(ty)
                val childTys = ty.getChildTypes()
                ArrayList<PendingTy>(Math.max(2 * childTys.size, 8)).apply {
                    childTys.forEach { childTy ->
                        this.add(PendingTy(childTy, childTy))
                    }
                }
            } else {
                ArrayList<PendingTy>().apply {
                    this.add(PendingTy(ty, ty))
                }
            }

            while (pendingTys.isNotEmpty()) {
                val pendingTy = pendingTys.removeLast()

                if (visitedTys.add(pendingTy.ty)) {
                    val resolvedMemberTy = (pendingTy.ty as? ITyResolvable)?.resolve(context) ?: pendingTy.ty

                    if (resolvedMemberTy !== pendingTy.ty) {
                        if (resolvedMemberTy is TyUnion) {
                            val childTys = resolvedMemberTy.getChildTypes()
                            pendingTys.ensureCapacity(pendingTys.size + childTys.size)
                            resolvedMemberTy.getChildTypes().forEach {
                                pendingTys.add(PendingTy(it, it))
                            }
                        } else {
                            pendingTys.add(PendingTy(resolvedMemberTy, pendingTy.unresolvedTy))
                        }
                    } else {
                        fn(pendingTy.unresolvedTy, pendingTy.ty)
                    }
                }
            }
        }

        inline fun eachUnresolved(context: SearchContext, ty: ITy, fn: (ITy) -> Unit) {
            eachUnresolved(context, ty) { unresolvedTy, _ -> fn(unresolvedTy) }
        }

        inline fun eachResolved(context: SearchContext, ty: ITy, fn: (ITy) -> Unit) {
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

                    if (resolvedMemberTy != pendingTy) {
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

        fun resolve(context: SearchContext, ty: ITy): ITy {
            if (!TyUnion.any(ty) { it is ITyResolvable && it.willResolve(context) }) {
                return ty
            }

            val tyCount = if (ty is TyUnion) ty.size else 1
            val memberTys = ArrayList<ITy>(maxOf(10, 3 * tyCount))

            eachResolved(context, ty) {
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
                TyUnion.union(context, memberTys)
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

    override fun equals(context: SearchContext, other: ITy): Boolean {
        if (other === Primitives.UNKNOWN) {
            return true
        }

        return resolve(context, other) is TyUnknown
    }

    override fun hashCode(): Int {
        return Constants.WORD_ANY.hashCode()
    }

    override fun contravariantOf(context: SearchContext, other: ITy, flags: Int): Boolean {
        return other !is TyMultipleResults
    }

    override fun guessMemberType(context: SearchContext, name: String): ITy? {
        return if (LuaSettings.instance.isUnknownIndexable) Primitives.UNKNOWN else null
    }

    override fun guessIndexerType(context: SearchContext, indexTy: ITy, exact: Boolean): ITy? {
        return if (LuaSettings.instance.isUnknownIndexable) Primitives.UNKNOWN else null
    }
}

class TyNil : Ty(TyKind.Nil) {

    override val booleanType = Primitives.FALSE

    override fun equals(context: SearchContext, other: ITy): Boolean {
        if (other === Primitives.NIL) {
            return true
        }

        return resolve(context, other) is TyNil
    }

    override fun contravariantOf(context: SearchContext, other: ITy, flags: Int): Boolean {
        return other.kind == TyKind.Nil || super.contravariantOf(context, other, flags)
    }
}

class TyVoid : Ty(TyKind.Void) {

    override fun equals(context: SearchContext, other: ITy): Boolean {
        if (other === Primitives.VOID) {
            return true
        }

        return resolve(context, other) is TyVoid
    }

    override fun contravariantOf(context: SearchContext, other: ITy, flags: Int): Boolean {
        return other.kind == TyKind.Void || super.contravariantOf(context, other, flags)
    }
}

class Primitives {
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
    }
}
