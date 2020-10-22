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
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext

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


typealias ProcessProblem = (targetElement: PsiElement?, sourceElement: PsiElement, message: String, highlightType: ProblemHighlightType?) -> Unit

class Problem (
        val targetElement: PsiElement?,
        val sourceElement: PsiElement,
        val message: String,
        highlightType: ProblemHighlightType? = ProblemHighlightType.GENERIC_ERROR_OR_WARNING
) {
    val highlightType: ProblemHighlightType

    init {
        this.highlightType = highlightType ?: ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    }
}

// PsiTreeUtil has getDepth but was only introduced in IntelliJ 192.4787.16, we presently support 172.0
private fun getDepth(element: PsiElement, topLevel: PsiElement?): Int {
    var depth = 0
    var parent: PsiElement? = element

    while (parent !== topLevel && parent != null) {
        ++depth
        parent = parent.parent
    }

    return depth
}

object ProblemUtil {
    private fun findHighlightElement(element: PsiElement): PsiElement? {
        return when (element) {
            is LuaLiteralExpr -> element
            is LuaTableExpr -> element
            is LuaParenExpr -> {
                return element.expr?.let { findHighlightElement(it) }
            }
            is LuaTableField -> {
                val valueExpr = element.exprList.last()
                return if (valueExpr is LuaParenExpr) findHighlightElement(valueExpr) else valueExpr
            }
            else -> null
        }
    }

    private fun acceptsShape(target: ITy, context: SearchContext): Boolean {
        Ty.eachResolved(target, context) {
            val resolved = Ty.resolve(it, context)

            if (resolved.isShape(context)) {
                return true
            }
        }

        return false
    }

    private fun contravariantOfShape(target: ITy, source: ITy, context: SearchContext, varianceFlags: Int, targetElement: PsiElement?, sourceElement: PsiElement?, processProblem: ProcessProblem?): Boolean {
        val sourceSubstitutor = source.getMemberSubstitutor(context)
        val targetSubstitutor = target.getMemberSubstitutor(context)

        var isContravariant = true

        target.processMembers(context, { _, targetMember ->
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
                        processProblem(
                                targetElement,
                                sourceElement ?: targetMember,
                                "Type mismatch. Missing member: '%s' of: '%s'".format(memberName, target.displayName),
                                null
                        )
                    }
                }

                return@processMembers true
            }

            val sourceMemberTy = (sourceMember.guessType(context) ?: Ty.UNKNOWN).let {
                if (sourceSubstitutor != null) it.substitute(sourceSubstitutor) else it
            }

            if (varianceFlags and TyVarianceFlags.STRICT_UNKNOWN != 0 || sourceMemberTy !is TyUnknown) {
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
                //}
            }

            true
        }, true)

        if (!isContravariant && processProblem == null) {
            return false
        }

        source.processMembers(context, { _, sourceMember ->
            val indexTy = sourceMember.guessIndexType(context)

            val sourceMemberTy = if (indexTy != null) {
                source.guessIndexerType(indexTy, context, true)
            } else {
                sourceMember.name?.let { source.guessMemberType(it, context) }
            } ?: Ty.UNKNOWN

            val targetMemberTy = if (indexTy != null) {
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
            }

            if (targetMemberTy == null) {
                return@processMembers true
            }

            // TODO: DRY
            if (varianceFlags and TyVarianceFlags.STRICT_UNKNOWN != 0 || sourceMemberTy !is TyUnknown) {
                if (varianceFlags and TyVarianceFlags.WIDEN_TABLES == 0) {
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
                } else {
                    val memberElement = if (processProblem != null && sourceElement != null) findHighlightElement(sourceMember.node.psi) else null

                    if (memberElement is LuaTableExpr) {
                        isContravariant = contravariantOf(targetMemberTy, sourceMemberTy, context, varianceFlags, targetElement, memberElement, processProblem!!) && isContravariant
                    } else if (!targetMemberTy.contravariantOf(sourceMemberTy, context, varianceFlags)) {
                        isContravariant = false

                        if (processProblem != null && sourceElement != null) {
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
                }
            }

            true
        }, true)

        return isContravariant
    }

    private fun contravariantOf(target: ITy, source: ITy, context: SearchContext, varianceFlags: Int, targetElement: PsiElement?, sourceElement: PsiElement, tyProblems: MutableMap<String, Collection<Problem>>): Boolean {
        val problems = mutableListOf<Problem>()
        tyProblems[target.displayName] = problems

        var isContravariant = true

        if (target is ITyArray) {
            val base = Ty.resolve(target.base, context)

            if (base is TyClass) {
                base.lazyInit(context)
            }

            if (source is TyClass) {
                source.lazyInit(context)

                if (source.isShape(context)) {
                    val baseIsShape = base.isShape(context)
                    val sourceIsInline = source is TyTable && source.table == sourceElement
                    val indexes = mutableSetOf<Int>()
                    var foundNumberIndexer = false

                    source.processMembers(context) { _, sourceMember ->
                        val indexTy = sourceMember.guessIndexType(context)
                        val highlightElement = if (sourceIsInline) {
                            sourceMember
                        } else sourceElement

                        if (indexTy == null || indexTy !is TyPrimitiveLiteral || indexTy.primitiveKind != TyPrimitiveKind.Number) {
                            isContravariant = false
                            problems.add(Problem(
                                    targetElement,
                                    highlightElement,
                                    "Type mismatch. Required: '${target.displayName}' Found non-array field '${sourceMember.name ?: "[${indexTy?.displayName}]"}'"
                            ))
                            return@processMembers true
                        }

                        if (indexTy == Ty.NUMBER) {
                            foundNumberIndexer = true
                        } else {
                            val index = indexTy.value.toIntOrNull()

                            if (index == null) {
                                isContravariant = false
                                problems.add(Problem(
                                        targetElement,
                                        highlightElement,
                                        "Type mismatch. Required: '%s' Found non-array field '[%s]'".format(target.displayName, indexTy.displayName)
                                ))
                                return@processMembers true
                            }

                            indexes.add(index)
                        }

                        val sourceFieldTypes = (sourceMember.guessType(context) ?: Ty.UNKNOWN).let {
                            if (it is TyMultipleResults) it.list else listOf(it)
                        }

                        val valueHighlightElement = if (sourceIsInline) {
                            if (sourceMember is LuaTableField) {
                                sourceMember.valueExpr ?: sourceMember
                            } else sourceMember
                        } else sourceElement

                        sourceFieldTypes.forEach { sourceFieldTy ->
                            if (sourceIsInline && baseIsShape && valueHighlightElement is LuaTableExpr) {
                                contravariantOf(base, sourceFieldTy, context, varianceFlags, targetElement, valueHighlightElement) { targetElement, sourceElement, message, highlightType ->
                                    isContravariant = false
                                    problems.add(Problem(targetElement, sourceElement, message, highlightType))
                                }
                            } else if (!base.contravariantOf(sourceFieldTy, context, varianceFlags)) {
                                isContravariant = false
                                problems.add(Problem(
                                        targetElement,
                                        valueHighlightElement,
                                        "Type mismatch. Required: '%s' Found: '%s'".format(base.displayName, sourceFieldTy.displayName)
                                ))
                            }
                        }

                        true
                    }

                    if (isContravariant && !foundNumberIndexer) {
                        indexes.sorted().forEachIndexed { index, i ->
                            if (i != index + 1) {
                                problems.add(Problem(
                                        targetElement,
                                        sourceElement,
                                        "Type mismatch. Required: '%s' Found: 'table<number, %s>'".format(target.displayName, base)
                                ))
                                return false
                            }
                        }
                    }

                    return isContravariant
                }
            }

            if (source !is ITyArray && (source !is ITyClass || !TyArray.isArray(source, context))) {
                problems.add(Problem(targetElement, sourceElement, "Type mismatch. Required: '%s' Found: '%s'".format(target.displayName, source.displayName)))
                return false
            }

            val baseIsShape = base.isShape(context)

            if (sourceElement is LuaTableExpr) {
                sourceElement.tableFieldList.forEach { sourceField ->
                    val sourceFieldTypes = (sourceField.guessType(context) ?: Ty.UNKNOWN).let {
                        if (it is TyMultipleResults) it.list else listOf(it)
                    }

                    sourceFieldTypes.forEach { sourceFieldTy ->
                        val sourceFieldElement = sourceField.valueExpr

                        if (baseIsShape && sourceFieldElement is LuaTableExpr) {
                            contravariantOf(base, sourceFieldTy, context, varianceFlags, targetElement, sourceFieldElement) { targetElement, sourceElement, message, highlightType ->
                                isContravariant = false
                                problems.add(Problem(targetElement, sourceElement, message, highlightType))
                            }
                        } else if (!base.contravariantOf(sourceFieldTy, context, varianceFlags)) {
                            isContravariant = false
                            problems.add(Problem(
                                    targetElement,
                                    sourceField,
                                    "Type mismatch. Required: '%s' Found: '%s'".format(base.displayName, sourceFieldTy.displayName)
                            ))
                        }
                    }
                }

                return isContravariant
            }
        }

        val base = if (target is ITyGeneric) target.base else target

        if (base is TyClass) {
            base.lazyInit(context)
        }

        if (source is TyTable && source.table == sourceElement && base.isShape(context)) {
            isContravariant = contravariantOfShape(target, source, context, varianceFlags or TyVarianceFlags.WIDEN_TABLES, targetElement, sourceElement) {
                shapeTargetElement, shapeSourceElement, message, highlightType -> problems.add(Problem(shapeTargetElement, shapeSourceElement, message, highlightType))
            }
        } else if (!target.contravariantOf(source, context, varianceFlags)) {
            isContravariant = false
            problems.add(Problem(targetElement, sourceElement, "Type mismatch. Required: '%s' Found: '%s'".format(target.displayName, source.displayName), ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
        }

        return isContravariant
    }

    fun contravariantOf(target: ITy, source: ITy, context: SearchContext, varianceFlags: Int, targetElement: PsiElement?, sourceElement: PsiElement, processProblem: ProcessProblem): Boolean {
        if (target === source) {
            return true
        }

        val tyProblems = mutableMapOf<String, Collection<Problem>>()
        val resolvedTarget = Ty.resolve(target, context)

        if (acceptsShape(resolvedTarget, context) && (source is ITyArray || (source is TyTable && source.table == sourceElement))) {
            if (TyUnion.isUnion(resolvedTarget, context) && resolvedTarget.contravariantOf(source, context, varianceFlags)) {
                return true
            }

            Ty.eachResolved(resolvedTarget, context) {
                if (contravariantOf(it, source, context, varianceFlags, targetElement, sourceElement, tyProblems)) {
                    return true
                }
            }
        } else if (contravariantOf(resolvedTarget, source, context, varianceFlags, targetElement, sourceElement, tyProblems)) {
            return true
        }

        // We consider the best matches to be the types with the deepest nested problems.
        val bestMatchingCandidates = mutableListOf<String>()
        var bestMatchingMinDepth = -1

        tyProblems.forEach { candidate, candidateProblems ->
            if (candidateProblems.isEmpty()) {
                return@forEach
            }

            var candidateMinDepth = Int.MAX_VALUE

            candidateProblems.forEach {
                val depth = getDepth(it.sourceElement, sourceElement)

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
            tyProblems[candidate]?.forEach {
                processProblem(it.targetElement, it.sourceElement, it.message, it.highlightType)
            }
        }

        return false
    }

    fun contravariantOfShape(target: ITy, source: ITy, context: SearchContext, varianceFlags: Int): Boolean {
        return contravariantOfShape(target, source, context, varianceFlags, null, null, null)
    }
}
