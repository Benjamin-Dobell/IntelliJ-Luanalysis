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

    private fun acceptsShape(target: ITy, context: SearchContext, varianceFlags: Int): Boolean {
        if (varianceFlags and TyVarianceFlags.NON_STRUCTURAL != 0) {
            return false
        }

        Ty.eachResolved(target, context) {
            if (it is ITyArray || it.isShape(context)) {
                return true
            }
        }

        return false
    }

    private fun contravariantOfShape(target: ITy, source: ITy, context: SearchContext, varianceFlags: Int, targetElement: PsiElement?, sourceElement: PsiElement?, processProblem: ProcessProblem?): Boolean {
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
                    it.substitute(targetSubstitutor)
                } else it
            }

            val sourceMember = if (indexTy != null) {
                source.findIndexer(indexTy, context, true)
            } else {
                targetMember.name?.let { source.findMember(it, context) }
            }

            if (sourceMember == null) {
                if (TyUnion.find(targetMemberTy, TyNil::class.java) == null) {
                    isContravariant = false
                    val memberName = targetMember.name ?: "[${targetMember.guessIndexType(context)?.displayName}]"
                    if (processProblem != null) {
                        processProblem(Problem(
                                targetElement,
                                sourceElement ?: targetMember,
                                "Type mismatch. Missing member: '%s' of: '%s'".format(memberName, target.displayName)
                        ))
                    }
                }

                return@processMembers true
            }

            val sourceMemberTy = (sourceMember.guessType(context) ?: Primitives.UNKNOWN).let {
                if (sourceSubstitutor != null) it.substitute(sourceSubstitutor) else it
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
                    val memberElement = if (processProblem != null && sourceElement != null) findHighlightElement(sourceMember.node.psi) else null

                    if (memberElement is LuaTableExpr) {
                        isContravariant = contravariantOf(targetMemberTy, sourceMemberTy, context, varianceFlags, targetElement, memberElement, processProblem!!) && isContravariant
                    } else if (!targetMemberTy.contravariantOf(sourceMemberTy, context, varianceFlags)) {
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
                source.guessIndexerType(indexTy, context, true)
            } else {
                sourceMember.name?.let { source.guessMemberType(it, context) }
            })?.let {
                if (sourceSubstitutor != null) it.substitute(sourceSubstitutor) else it
            } ?: Primitives.UNKNOWN

            val targetMemberTy = (if (indexTy != null) {
                val targetMember = target.findIndexer(indexTy, context)

                if (targetMember?.guessIndexType(context)?.equals(indexTy, context) == true) {
                    // If the target index type == source index type, then we have already checked compatibility of this member above.
                    return@processMembers true
                }

                targetMember?.guessType(context)
            } else {
                sourceMember.name?.let {
                    val targetMember = target.findMember(it, context)

                    if (targetMember?.getName() == it) {
                        // If the target index type == source member name, then we have already checked compatibility of this member above.
                        return@processMembers true
                    }

                    targetMember?.guessType(context)
                }
            })?.let {
                if (targetSubstitutor != null) it.substitute(targetSubstitutor) else it
            }

            if (targetMemberTy == null) {
                return@processMembers true
            }

            // TODO: DRY
            if (varianceFlags and TyVarianceFlags.STRICT_UNKNOWN != 0 || !sourceMemberTy.isUnknown) {
                if (varianceFlags and TyVarianceFlags.WIDEN_TABLES == 0) {
                    if (!targetMemberTy.equals(sourceMemberTy, context)) {
                        isContravariant = false

                        if (processProblem != null && sourceElement != null) {
                            val memberElement = findHighlightElement(sourceMember.node.psi)

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
                    val memberElement = if (processProblem != null && sourceElement != null) findHighlightElement(sourceMember.node.psi) else null

                    if (memberElement is LuaTableExpr) {
                        isContravariant = contravariantOf(targetMemberTy, sourceMemberTy, context, varianceFlags, targetElement, memberElement, processProblem!!) && isContravariant
                    } else if (!targetMemberTy.contravariantOf(sourceMemberTy, context, varianceFlags)) {
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

    private fun contravariantOfUnit(targetTy: ITy, sourceUnitTy: ITy, context: SearchContext, varianceFlags: Int, targetElement: PsiElement?, sourceElement: PsiElement, processProblem: ProcessProblem): Boolean {
        if (varianceFlags and TyVarianceFlags.STRICT_UNKNOWN == 0 && sourceUnitTy.isUnknown) {
            return true
        }

        var isContravariant = true

        if (targetTy is ITyArray) {
            val base = Ty.resolve(targetTy.base, context)

            if (base is TyClass) {
                base.lazyInit(context)
            }

            if (sourceUnitTy is TyClass) {
                sourceUnitTy.lazyInit(context)

                if ((varianceFlags and TyVarianceFlags.NON_STRUCTURAL == 0 || sourceUnitTy.isAnonymousTable) && sourceUnitTy.isShape(context)) {
                    val sourceIsInline = sourceUnitTy is TyTable && sourceUnitTy.table == sourceElement
                    val indexes = sortedMapOf<Int, PsiElement>()
                    var foundNumberIndexer = false

                    sourceUnitTy.processMembers(context) { _, sourceMember ->
                        val indexTy = sourceMember.guessIndexType(context)
                        val highlightElement = if (sourceIsInline) {
                            sourceMember
                        } else sourceElement

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
                            if (sourceMember is LuaTableField) {
                                sourceMember.valueExpr ?: sourceMember
                            } else sourceMember
                        } else sourceElement

                        sourceFieldTypes.forEach { sourceFieldTy ->
                            contravariantOf(base, sourceFieldTy, context, varianceFlags, targetElement, valueHighlightElement) { problem ->
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

            if (sourceUnitTy !is ITyArray && (sourceUnitTy !is ITyClass || !TyArray.isArray(sourceUnitTy, context))) {
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

            val baseAcceptsShape = (varianceFlags and TyVarianceFlags.NON_STRUCTURAL == 0 || sourceUnitTy.isAnonymousTable) && base.isShape(context)

            if (sourceElement is LuaTableExpr) {
                sourceElement.tableFieldList.forEach { sourceField ->
                    val sourceFieldTypes = (sourceField.guessType(context) ?: Primitives.UNKNOWN).let {
                        if (it is TyMultipleResults) it.list else listOf(it)
                    }

                    sourceFieldTypes.forEach { sourceFieldTy ->
                        val sourceFieldElement = sourceField.valueExpr

                        if (baseAcceptsShape && sourceFieldElement is LuaTableExpr) {
                            contravariantOf(base, sourceFieldTy, context, varianceFlags, targetElement, sourceFieldElement) { problem ->
                                isContravariant = false
                                processProblem(problem)
                            }
                        } else if (!base.contravariantOf(sourceFieldTy, context, varianceFlags)) {
                            isContravariant = false
                            processProblem(
                                Problem(
                                    targetElement,
                                    sourceField,
                                    "Type mismatch. Required: '%s' Found: '%s'".format(base.displayName, sourceFieldTy.displayName),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                                )
                            )
                        }
                    }
                }

                return isContravariant
            }
        }

        val base = if (targetTy is ITyGeneric) targetTy.base else targetTy

        if (base is TyClass) {
            base.lazyInit(context)
        }

        if (varianceFlags and TyVarianceFlags.NON_STRUCTURAL == 0 && sourceUnitTy is TyTable && sourceUnitTy.table == sourceElement && base.isShape(context)) {
            isContravariant = contravariantOfShape(
                targetTy,
                sourceUnitTy,
                context,
                varianceFlags or TyVarianceFlags.WIDEN_TABLES,
                targetElement,
                sourceElement,
                processProblem
            )
        } else if (!targetTy.contravariantOf(sourceUnitTy, context, varianceFlags)) {
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
                            contravariantOfUnit(targetTy, leftSourceTy, context, varianceFlags, null, leftExpression, processProblem)
                        }
                        else -> {
                            var leftFalseyTy: ITy = Primitives.VOID

                            Ty.eachResolved(leftSourceTy, context) {
                                if (it == Primitives.BOOLEAN) {
                                    leftFalseyTy = leftFalseyTy.union(Primitives.FALSE, context)
                                } else if (it.booleanType != Primitives.TRUE) {
                                    leftFalseyTy = leftFalseyTy.union(it, context)
                                }
                            }

                            val leftContravariant = contravariantOfUnit(targetTy, leftFalseyTy, context, varianceFlags, null, leftExpression, processProblem)
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
                            val leftTargetTy = TyUnion.union(listOf(targetTy, Primitives.FALSE, Primitives.NIL), context)
                            val leftContravariant = expressionTraversingContravariantOf(leftTargetTy, leftSourceTy, context, varianceFlags, leftExpression, processProblem)

                            val rightSourceTy = context.withIndex(0) { rightExpression.guessType(context) } ?: Primitives.UNKNOWN
                            expressionTraversingContravariantOf(targetTy, rightSourceTy, context, varianceFlags, rightExpression, processProblem) && leftContravariant
                        }
                    }
                }
            }
        }

        if (sourceExpression !is LuaTableExpr) {
            return contravariantOfUnit(targetTy, sourceTy, context, varianceFlags, null, sourceExpression, processProblem)
        }

        val targetCandidateProblems = mutableMapOf<String, Collection<Problem>>()

        Ty.eachResolved(targetTy, context) {
            val candidateProblems = mutableListOf<Problem>()
            targetCandidateProblems[it.displayName] = candidateProblems

            if (contravariantOfUnit(it, sourceTy, context, varianceFlags, null, sourceExpression) { candidateProblems.add(it) }) {
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

    fun contravariantOf(target: ITy, source: ITy, context: SearchContext, varianceFlags: Int, targetElement: PsiElement?, sourceElement: PsiElement, processProblem: ProcessProblem): Boolean {
        if (target === source) {
            return true
        }

        val resolvedTarget = Ty.resolve(target, context)

        if (isTraversableExpression(sourceElement) && acceptsShape(resolvedTarget, context, varianceFlags)) {
            if (TyUnion.isUnion(resolvedTarget, context) && resolvedTarget.contravariantOf(source, context, varianceFlags)) {
                return true
            }

            return expressionTraversingContravariantOf(target, source, context, varianceFlags, sourceElement, processProblem)
        }

        return contravariantOfUnit(resolvedTarget, source, context, varianceFlags, targetElement, sourceElement, processProblem)
    }

    fun contravariantOfShape(target: ITy, source: ITy, context: SearchContext, varianceFlags: Int): Boolean {
        return contravariantOfShape(target, source, context, varianceFlags, null, null, null)
    }

    fun unionAwareProblemProcessor(ownerTy: ITy, targetTy: ITy, context: SearchContext, processProblem: ProcessProblem): ProcessProblem {
        if (!TyUnion.isUnion(ownerTy, context)) {
            return processProblem
        }

        return { problem ->
            problem.message = "${problem.message}, on union candidate ${targetTy.displayName}"
            processProblem(problem)
        }
    }
}
