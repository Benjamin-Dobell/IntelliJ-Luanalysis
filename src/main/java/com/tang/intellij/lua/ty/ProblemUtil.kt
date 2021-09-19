/*
 * Copyright (c) 2020
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
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

data class Problem(
    val targetElement: PsiElement?,
    val sourceElement: PsiElement,
    var message: String,
    var highlightType: ProblemHighlightType? = null
)

typealias ProcessProblem = (problem: Problem) -> Unit

object ProblemUtil {
    private fun findHighlightElement(element: PsiElement): PsiElement? {
        return when (element) {
            is LuaLiteralExpr -> element
            is LuaTableExpr -> element
            is LuaParenExpr -> {
                return element.expression?.let { findHighlightElement(it) }
            }
            is LuaTableField -> {
                val valueExpr = element.expressionList.last()
                return if (valueExpr is LuaParenExpr) findHighlightElement(valueExpr) else valueExpr
            }
            else -> null
        }
    }

    private fun acceptsShape(context: SearchContext, target: ITy, varianceFlags: Int): Boolean {
        if (varianceFlags and TyVarianceFlags.NON_STRUCTURAL != 0) {
            return false
        }

        Ty.eachResolved(context, target) {
            if (it is ITyArray || it.isShape(context)) {
                return true
            }
        }

        return false
    }

    private fun contravariantOfShape(
        context: SearchContext,
        target: ITy,
        source: ITy,
        varianceFlags: Int,
        targetElement: PsiElement?,
        sourceElement: PsiElement?,
        processProblem: ProcessProblem?
    ): Boolean {
        if (varianceFlags and TyVarianceFlags.STRICT_UNKNOWN == 0 && source.isUnknown) {
            return true
        }

        if (source !is ITyClass || source is ITyPrimitive) {
            if (processProblem != null && sourceElement != null) {
                processProblem(
                    Problem(
                        targetElement,
                        sourceElement,
                        "Type mismatch. Required: '%s' Found: '%s'".format(target.displayName, source.displayName)
                    )
                )
            }

            return false
        }

        val sourceSubstitutor = source.getMemberSubstitutor(context)
        val targetSubstitutor = target.getMemberSubstitutor(context)

        var isContravariant = true

        target.processMembers(context, true) { _, targetMember ->
            val indexTy = targetMember.guessIndexType(context)

            val targetMemberTy = targetMember.guessType(context).let {
                if (it == null) {
                    return@processMembers true
                }

                if (targetSubstitutor != null) {
                    it.substitute(context, targetSubstitutor)
                } else it
            }

            val sourceMember = if (indexTy != null) {
                source.findIndexer(context, indexTy, true)
            } else {
                targetMember.name?.let { source.findMember(context, it) }
            }

            if (sourceMember == null) {
                if (TyUnion.find(targetMemberTy, TyNil::class.java) == null) {
                    isContravariant = false
                    val memberName = targetMember.name ?: "[${targetMember.guessIndexType(context)?.displayName}]"
                    if (processProblem != null) {
                        processProblem(Problem(
                                targetElement,
                                sourceElement ?: targetMember.psi,
                                "Type mismatch. Missing member: '%s' of: '%s'".format(memberName, target.displayName)
                        ))
                    }
                }

                return@processMembers true
            }

            val sourceMemberTy = (sourceMember.guessType(context) ?: Primitives.UNKNOWN).let {
                if (sourceSubstitutor != null) it.substitute(context, sourceSubstitutor) else it
            }

            if (varianceFlags and TyVarianceFlags.STRICT_UNKNOWN != 0 || !sourceMemberTy.isUnknown) {
                // TODO: Always allowing widening is unsound. However, usage of Luanalysis without shape widening is presently impractical as we lack required
                //       functionality such as getter/setter types, readonly properties and maybe use-site variance. Mind you, even TypeScript fails at this.
                /*if (varianceFlags and TyVarianceFlags.WIDEN_TABLES == 0) {
                    if (!targetMemberTy.equals(sourceMemberTy, context)) {
                        isContravariant = false

                        if (processProblem != null && sourceElement != null) {
                            val memberElement = findHighlightElement(sourceMember.node.psi)

                            processProblem(
                                    targetElement,
                                    memberElement ?: sourceElement,
                                    "Type mismatch. Required: '%s' Found: '%s'".format(targetMemberTy.displayName, sourceMemberTy.displayName),
                                    null
                            )
                        } else {
                            return@processMembers false
                        }
                    }
                } else {*/
                    val memberElement = if (processProblem != null && sourceElement != null) findHighlightElement(sourceMember.psi.node.psi) else null

                    if (memberElement is LuaTableExpr) {
                        isContravariant = contravariantOf(
                            context,
                            targetMemberTy,
                            sourceMemberTy,
                            varianceFlags,
                            targetElement,
                            memberElement,
                            processProblem!!
                        ) && isContravariant
                    } else if (!targetMemberTy.contravariantOf(context, sourceMemberTy, varianceFlags)) {
                        isContravariant = false

                        if (processProblem != null && sourceElement != null) {
                            processProblem(Problem(
                                    targetElement,
                                    memberElement ?: sourceElement,
                                    "Type mismatch. Required: '%s' Found: '%s'".format(targetMemberTy.displayName, sourceMemberTy.displayName)
                            ))
                        } else {
                            return@processMembers false
                        }
                    }
                //}
            }

            true
        }

        if (!isContravariant && processProblem == null) {
            return false
        }

        source.processMembers(context, true) { _, sourceMember ->
            val indexTy = sourceMember.guessIndexType(context)

            val sourceMemberTy = (if (indexTy != null) {
                source.guessIndexerType(context, indexTy, true)
            } else {
                sourceMember.name?.let { source.guessMemberType(context, it) }
            })?.let {
                if (sourceSubstitutor != null) it.substitute(context, sourceSubstitutor) else it
            } ?: Primitives.UNKNOWN

            val targetMemberTy = (if (indexTy != null) {
                val targetMember = target.findIndexer(context, indexTy)

                if (targetMember?.guessIndexType(context)?.equals(context, indexTy) == true) {
                    // If the target index type == source index type, then we have already checked compatibility of this member above.
                    return@processMembers true
                }

                targetMember?.guessType(context)
            } else {
                sourceMember.name?.let {
                    val targetMember = target.findMember(context, it)

                    if (targetMember?.name == it) {
                        // If the target index type == source member name, then we have already checked compatibility of this member above.
                        return@processMembers true
                    }

                    targetMember?.guessType(context)
                }
            })?.let {
                if (targetSubstitutor != null) it.substitute(context, targetSubstitutor) else it
            }

            if (targetMemberTy == null) {
                return@processMembers true
            }

            // TODO: DRY
            if (varianceFlags and TyVarianceFlags.STRICT_UNKNOWN != 0 || !sourceMemberTy.isUnknown) {
                if (varianceFlags and TyVarianceFlags.WIDEN_TABLES == 0) {
                    if (!targetMemberTy.equals(context, sourceMemberTy)) {
                        isContravariant = false

                        if (processProblem != null && sourceElement != null) {
                            val memberElement = findHighlightElement(sourceMember.psi.node.psi)

                            processProblem(Problem(
                                    targetElement,
                                    memberElement ?: sourceElement,
                                    "Type mismatch. Required: '%s' Found: '%s'".format(targetMemberTy.displayName, sourceMemberTy.displayName)
                            ))
                        } else {
                            return@processMembers false
                        }
                    }
                } else {
                    val memberElement = if (processProblem != null && sourceElement != null) findHighlightElement(sourceMember.psi.node.psi) else null

                    if (memberElement is LuaTableExpr) {
                        isContravariant = contravariantOf(
                            context,
                            targetMemberTy,
                            sourceMemberTy,
                            varianceFlags,
                            targetElement,
                            memberElement,
                            processProblem!!
                        ) && isContravariant
                    } else if (!targetMemberTy.contravariantOf(context, sourceMemberTy, varianceFlags)) {
                        isContravariant = false

                        if (processProblem != null && sourceElement != null) {
                            processProblem(Problem(
                                    targetElement,
                                    memberElement ?: sourceElement,
                                    "Type mismatch. Required: '%s' Found: '%s'".format(targetMemberTy.displayName, sourceMemberTy.displayName)
                            ))
                        } else {
                            return@processMembers false
                        }
                    }
                }
            }

            true
        }

        return isContravariant
    }

    private fun contravariantOfUnit(
        context: SearchContext,
        targetTy: ITy,
        sourceUnitTy: ITy,
        varianceFlags: Int,
        targetElement: PsiElement?,
        sourceElement: PsiElement,
        processProblem: ProcessProblem
    ): Boolean {
        val resolvedSourceTy = Ty.resolve(context, sourceUnitTy)

        if (varianceFlags and TyVarianceFlags.STRICT_UNKNOWN == 0 && resolvedSourceTy.isUnknown) {
            return true
        }

        val resolvedTargetTy = Ty.resolve(context, targetTy)
        var isContravariant = true

        if (resolvedTargetTy is ITyArray) {
            val base = Ty.resolve(context, resolvedTargetTy.base)

            if (base is TyClass) {
                base.lazyInit(context)
            }

            if (resolvedSourceTy is TyClass) {
                resolvedSourceTy.lazyInit(context)

                if ((varianceFlags and TyVarianceFlags.NON_STRUCTURAL == 0 || resolvedSourceTy.isAnonymousTable) && resolvedSourceTy.isShape(context)) {
                    val sourceIsInline = resolvedSourceTy is TyTable && resolvedSourceTy.table == sourceElement
                    val indexes = sortedMapOf<Int, PsiElement>()
                    var foundNumberIndexer = false

                    resolvedSourceTy.processMembers(context) { _, sourceMember ->
                        val indexTy = sourceMember.guessIndexType(context)
                        val highlightElement = if (sourceIsInline) {
                            sourceMember.psi
                        } else {
                            sourceElement
                        }

                        if (indexTy == null || indexTy !is TyPrimitiveLiteral || indexTy.primitiveKind != TyPrimitiveKind.Number) {
                            isContravariant = false
                            processProblem(
                                Problem(
                                    targetElement,
                                    highlightElement,
                                    "Type mismatch. Required: '${targetTy.displayName}' Found non-array field '${sourceMember.name ?: "[${indexTy?.displayName}]"}'",
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                                )
                            )
                            return@processMembers true
                        }

                        if (indexTy == Primitives.NUMBER) {
                            foundNumberIndexer = true
                        } else {
                            val index = indexTy.value.toIntOrNull()

                            if (index == null || index < 1) {
                                isContravariant = false
                                processProblem(
                                    Problem(
                                        targetElement,
                                        highlightElement,
                                        "Type mismatch. Required array index: '%s' Found non-array field '[%s]'".format(targetTy.displayName, indexTy.displayName),
                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                                    )
                                )
                                return@processMembers true
                            }

                            indexes.put(index, highlightElement)
                        }

                        val sourceFieldTypes = (sourceMember.guessType(context) ?: Primitives.UNKNOWN).let {
                            if (it is TyMultipleResults) it.list else listOf(it)
                        }

                        val valueHighlightElement = if (sourceIsInline) {
                            (sourceMember as? LuaTableField)?.valueExpr ?: sourceMember.psi
                        } else sourceElement

                        sourceFieldTypes.forEach { sourceFieldTy ->
                            contravariantOf(context, resolvedTargetTy.base, sourceFieldTy, varianceFlags, targetElement, valueHighlightElement) { problem ->
                                isContravariant = false
                                processProblem(problem)
                            }
                        }

                        true
                    }

                    if (isContravariant && !foundNumberIndexer) {
                        var previousIndex = 0
                        indexes.all { (index, highlightElement) ->
                            val expectedIndex = previousIndex + 1

                            if (index != expectedIndex) {
                                processProblem(
                                    Problem(
                                        targetElement,
                                        highlightElement,
                                        "Type mismatch. Required array index: '%s' Found non-contiguous index: '%s'".format(expectedIndex, index),
                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                                    )
                                )
                                return false
                            }

                            previousIndex = index
                            true
                        }
                    }

                    return isContravariant
                }
            }

            if (resolvedSourceTy !is ITyArray && (resolvedSourceTy !is ITyClass || !TyArray.isArray(context, resolvedSourceTy))) {
                processProblem(
                    Problem(
                        targetElement,
                        sourceElement,
                        "Type mismatch. Required: '%s' Found: '%s'".format(targetTy.displayName, sourceUnitTy.displayName),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                    )
                )
                return false
            }

            val baseAcceptsShape = (varianceFlags and TyVarianceFlags.NON_STRUCTURAL == 0 || resolvedSourceTy.isAnonymousTable) && base.isShape(context)

            if (sourceElement is LuaTableExpr) {
                sourceElement.tableFieldList.forEach { sourceField ->
                    val sourceFieldTypes = (sourceField.guessType(context) ?: Primitives.UNKNOWN).let {
                        if (it is TyMultipleResults) it.list else listOf(it)
                    }

                    sourceFieldTypes.forEach { sourceFieldTy ->
                        val sourceFieldElement = sourceField.valueExpr

                        if (baseAcceptsShape && sourceFieldElement is LuaTableExpr) {
                            contravariantOf(context, resolvedTargetTy.base, sourceFieldTy, varianceFlags, targetElement, sourceFieldElement) { problem ->
                                isContravariant = false
                                processProblem(problem)
                            }
                        } else if (!base.contravariantOf(context, sourceFieldTy, varianceFlags)) {
                            isContravariant = false
                            processProblem(
                                Problem(
                                    targetElement,
                                    sourceField,
                                    "Type mismatch. Required: '%s' Found: '%s'".format(resolvedTargetTy.base.displayName, sourceFieldTy.displayName),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                                )
                            )
                        }
                    }
                }

                return isContravariant
            }
        }

        val base = if (resolvedTargetTy is ITyGeneric) resolvedTargetTy.base else resolvedTargetTy

        if (base is TyClass) {
            base.lazyInit(context)
        }

        if (varianceFlags and TyVarianceFlags.NON_STRUCTURAL == 0 && resolvedSourceTy is TyTable && resolvedSourceTy.table == sourceElement && base.isShape(context)) {
            isContravariant = contravariantOfShape(
                context,
                resolvedTargetTy,
                resolvedSourceTy,
                varianceFlags or TyVarianceFlags.WIDEN_TABLES,
                targetElement,
                sourceElement,
                processProblem
            )
        } else if (!resolvedTargetTy.contravariantOf(context, resolvedSourceTy, varianceFlags)) {
            isContravariant = false
            processProblem(
                Problem(
                    targetElement,
                    sourceElement,
                    "Type mismatch. Required: '%s' Found: '%s'".format(targetTy.displayName, sourceUnitTy.displayName),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                )
            )
        }

        return isContravariant
    }

    @ExperimentalContracts
    private fun isTraversableExpression(element: PsiElement): Boolean {
        contract {
            returns(true) implies (element is LuaExpression<*>)
        }
        return element is LuaTableExpr || element is LuaParenExpr || (element as? LuaBinaryExpr)?.operationType?.let { it == LuaTypes.AND || it == LuaTypes.OR } == true
    }

    private fun expressionTraversingContravariantOf(
        targetTy: ITy,
        sourceTy: ITy,
        context: SearchContext,
        varianceFlags: Int,
        sourceExpression: LuaExpression<*>,
        processProblem: ProcessProblem
    ): Boolean {
        if (sourceExpression is LuaParenExpr) {
            sourceExpression.expression?.let {
                return expressionTraversingContravariantOf(targetTy, sourceTy, context, varianceFlags, it, processProblem)
            }
        } else if (sourceExpression is LuaBinaryExpr) {
            val op = sourceExpression.operationType
            val leftExpression = sourceExpression.left
            val rightExpression = sourceExpression.right

            if (leftExpression != null && rightExpression != null) {
                if (op == LuaTypes.AND) {
                    val leftSourceTy = context.withIndex(0) { leftExpression.guessType(context) } ?: Primitives.UNKNOWN
                    return when (leftSourceTy.booleanType) {
                        Primitives.TRUE -> {
                            val rightSourceTy = context.withIndex(0) { rightExpression.guessType(context) } ?: Primitives.UNKNOWN
                            expressionTraversingContravariantOf(targetTy, rightSourceTy, context, varianceFlags, rightExpression, processProblem)
                        }
                        Primitives.FALSE -> {
                            contravariantOfUnit(context, targetTy, leftSourceTy, varianceFlags, null, leftExpression, processProblem)
                        }
                        else -> {
                            var leftFalseyTy: ITy = Primitives.VOID

                            Ty.eachUnresolved(context, leftSourceTy) { unresolvedTy, resolvedTy ->
                                if (resolvedTy == Primitives.BOOLEAN) {
                                    leftFalseyTy = leftFalseyTy.union(context, Primitives.FALSE)
                                } else if (resolvedTy.booleanType != Primitives.TRUE) {
                                    leftFalseyTy = leftFalseyTy.union(context, unresolvedTy)
                                }
                            }

                            val leftContravariant = contravariantOfUnit(context, targetTy, leftFalseyTy, varianceFlags, null, leftExpression, processProblem)
                            val rightSourceTy = context.withIndex(0) { rightExpression.guessType(context) } ?: Primitives.UNKNOWN
                            expressionTraversingContravariantOf(targetTy, rightSourceTy, context, varianceFlags, rightExpression, processProblem) && leftContravariant
                        }
                    }
                } else if (op == LuaTypes.OR) {
                    val leftSourceTy = context.withIndex(0) { leftExpression.guessType(context) } ?: Primitives.UNKNOWN
                    return when (leftSourceTy.booleanType) {
                        Primitives.TRUE -> {
                            expressionTraversingContravariantOf(targetTy, leftSourceTy, context, varianceFlags, leftExpression, processProblem)
                        }
                        Primitives.FALSE -> {
                            val rightSourceTy = context.withIndex(0) { rightExpression.guessType(context) } ?: Primitives.UNKNOWN
                            expressionTraversingContravariantOf(targetTy, rightSourceTy, context, varianceFlags, rightExpression, processProblem)
                        }
                        else -> {
                            val leftTargetTy = TyUnion.union(context, listOf(targetTy, Primitives.FALSE, Primitives.NIL))
                            val leftContravariant = expressionTraversingContravariantOf(leftTargetTy, leftSourceTy, context, varianceFlags, leftExpression, processProblem)

                            val rightSourceTy = context.withIndex(0) { rightExpression.guessType(context) } ?: Primitives.UNKNOWN
                            expressionTraversingContravariantOf(targetTy, rightSourceTy, context, varianceFlags, rightExpression, processProblem) && leftContravariant
                        }
                    }
                }
            }
        }

        if (sourceExpression !is LuaTableExpr) {
            return contravariantOfUnit(context, targetTy, sourceTy, varianceFlags, null, sourceExpression, processProblem)
        }

        val targetCandidateProblems = mutableMapOf<String, Collection<Problem>>()

        Ty.eachUnresolved(context, targetTy) { it ->
            val candidateProblems = mutableListOf<Problem>()
            targetCandidateProblems[it.displayName] = candidateProblems

            if (contravariantOfUnit(context, it, sourceTy, varianceFlags, null, sourceExpression) { candidateProblems.add(it) }) {
                return true
            }
        }

        // We consider the best matches to be the types with the deepest nested problems.
        val bestMatchingCandidates = mutableListOf<String>()
        var bestMatchingMinDepth = -1

        targetCandidateProblems.forEach { candidate, candidateProblems ->
            if (candidateProblems.isEmpty()) {
                return@forEach
            }

            var candidateMinDepth = Int.MAX_VALUE

            // Count with matching depth

            candidateProblems.forEach {
                val depth = PsiTreeUtil.getDepth(it.sourceElement, sourceExpression)

                if (depth < candidateMinDepth) {
                    candidateMinDepth = depth
                }
            }

            if (candidateMinDepth >= bestMatchingMinDepth) {
                if (candidateMinDepth > bestMatchingMinDepth) {
                    bestMatchingCandidates.clear()
                    bestMatchingMinDepth = candidateMinDepth
                }

                bestMatchingCandidates.add(candidate)
            }
        }

        bestMatchingCandidates.forEach { candidate ->
            val processCandidateProblem = if (bestMatchingCandidates.size > 1) {
                { problem: Problem ->
                    problem.message = "${problem.message}, on union candidate ${candidate}"
                    processProblem(problem)
                }
            } else {
                processProblem
            }

            targetCandidateProblems[candidate]?.forEach {
                processCandidateProblem(it)
            }
        }

        return false
    }

    fun contravariantOf(
        context: SearchContext,
        target: ITy,
        source: ITy,
        varianceFlags: Int,
        targetElement: PsiElement?,
        sourceElement: PsiElement,
        processProblem: ProcessProblem
    ): Boolean {
        if (target === source) {
            return true
        }

        if (isTraversableExpression(sourceElement)) {
            val resolvedTarget = Ty.resolve(context, target)

            if (acceptsShape(context, resolvedTarget, varianceFlags)) {
                if (TyUnion.isUnion(context, resolvedTarget) && resolvedTarget.contravariantOf(context, source, varianceFlags)) {
                    return true
                }

                return expressionTraversingContravariantOf(target, source, context, varianceFlags, sourceElement, processProblem)
            }
        }

        return contravariantOfUnit(context, target, source, varianceFlags, targetElement, sourceElement, processProblem)
    }

    fun contravariantOfShape(context: SearchContext, target: ITy, source: ITy, varianceFlags: Int): Boolean {
        return contravariantOfShape(context, target, source, varianceFlags, null, null, null)
    }

    fun unionAwareProblemProcessor(context: SearchContext, ownerTy: ITy, targetTy: ITy, processProblem: ProcessProblem): ProcessProblem {
        if (!TyUnion.isUnion(context, ownerTy)) {
            return processProblem
        }

        return { problem ->
            problem.message = "${problem.message}, on union candidate ${targetTy.displayName}"
            processProblem(problem)
        }
    }
}
