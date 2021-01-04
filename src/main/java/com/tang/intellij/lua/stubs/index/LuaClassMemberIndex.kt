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

package com.tang.intellij.lua.stubs.index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.IntStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import com.tang.intellij.lua.comment.psi.*
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*

typealias ProcessClassMember = (member: LuaClassMember, ownerTy: ITyClass) -> Boolean

class LuaClassMemberIndex : IntStubIndexExtension<LuaClassMember>() {
    override fun getKey() = StubKeys.CLASS_MEMBER

    override fun get(s: Int, project: Project, scope: GlobalSearchScope): Collection<LuaClassMember> =
            StubIndex.getElements(StubKeys.CLASS_MEMBER, s, project, scope, LuaClassMember::class.java)

    companion object {
        val instance = LuaClassMemberIndex()

        private fun processKey(type: ITyClass, key: String, context: SearchContext, process: ProcessClassMember): Boolean {
            if (context.isDumb) {
                return false
            }

            LuaClassMemberIndex.instance.get(key.hashCode(), context.project, context.scope).forEach {
                if (!process(it, type)) {
                    return false
                }
            }

            return true
        }

        private fun processClassKey(owner: ITyClass, className: String, key: String, context: SearchContext, process: ProcessClassMember, deep: Boolean): Boolean {
            val classKey = "$className$key"

            if (!processKey(owner, classKey, context, process)) {
                return false
            }

            if (deep) {
                owner.lazyInit(context)

                val notFound = owner.processAlias({ aliasedName ->
                    LuaClassIndex.find(aliasedName, context)?.type?.let { aliasedClass ->
                        processClassKey(aliasedClass, aliasedName, key, context, process, deep)
                    } ?: true
                })

                if (!notFound) {
                    return false
                }

                return Ty.processSuperClasses(owner, context) { superType ->
                    val superClass = (if (superType is ITyGeneric) superType.base else superType) as? ITyClass
                    if (superClass != null) {
                        processClassKey(superClass, superClass.className, key, context, process, false)
                    } else true
                }
            }

            return true
        }

        private fun processClassKey(cls: ITyClass, key: String, context: SearchContext, process: ProcessClassMember, deep: Boolean = true): Boolean {
            return if (cls is TyGenericParameter) {
                (cls.superClass as? ITyClass)?.let { processClassKey(it, it.className, key, context, process, deep) } ?: true
            } else {
                processClassKey(cls, cls.className, key, context, process, deep)
            }
        }

        fun getMembers(className: String, context: SearchContext): Collection<LuaClassMember> {
            if (context.isDumb) {
                return listOf()
            }

            return instance.get(className.hashCode(), context.project, context.scope)
        }

        fun processMember(cls: ITyClass, fieldName: String, context: SearchContext, process: ProcessClassMember, deep: Boolean = true): Boolean {
            return processClassKey(cls, "*$fieldName", context, process, deep)
        }

        fun findMember(cls: ITyClass, fieldName: String, context: SearchContext, searchIndexers: Boolean = true, deep: Boolean = true): LuaClassMember? {
            var perfect: LuaClassMember? = null
            var tagField: LuaDocTagField? = null
            var tableField: LuaTableField? = null

            var shallowestTy: ITyClass? = null

            processMember(cls, fieldName, context, { member, ownerTy ->
                if (shallowestTy != null && ownerTy !== shallowestTy) {
                    return@processMember false
                }

                when (member) {
                    is LuaDocTagField -> {
                        shallowestTy = ownerTy
                        tagField = member
                        false
                    }
                    is LuaTableField -> {
                        shallowestTy = ownerTy
                        tableField = member
                        true
                    }
                    else -> {
                        var isTypeOverride = false

                        if (member is LuaIndexExpr) {
                            val assignStat = member.assignStat
                            val comment = assignStat?.comment

                            if (comment != null) {
                                val tagType = comment.tagType

                                if (tagType != null) {
                                    val docTyCount = tagType.typeList?.tyList?.size
                                    isTypeOverride = docTyCount != null && assignStat.getIndexFor(member) < docTyCount
                                } else {
                                    isTypeOverride = (
                                            comment.findTag(LuaDocTagOverload::class.java)
                                                ?: comment.findTag(LuaDocTagParam::class.java)
                                                ?: comment.findTag(LuaDocTagReturn::class.java)
                                                ?: comment.findTag(LuaDocTagVararg::class.java)
                                            ) != null
                                }
                            }
                        } else if (member is LuaClassMethodDefStat) {
                            val comment = member.comment

                            if (comment != null) {
                                isTypeOverride = (
                                        comment.findTag(LuaDocTagOverload::class.java)
                                            ?: comment.findTag(LuaDocTagParam::class.java)
                                            ?: comment.findTag(LuaDocTagReturn::class.java)
                                            ?: comment.findTag(LuaDocTagVararg::class.java)
                                        ) != null
                            }
                        }

                        if (isTypeOverride) {
                            shallowestTy = ownerTy
                            perfect = member
                        } else if (perfect == null) {
                            perfect = member
                        }

                        true
                    }
                }
            }, deep)

            if (tagField != null) return tagField
            if (tableField != null) return tableField
            if (perfect != null) return perfect

            return if (searchIndexers) {
                findIndexer(cls, TyPrimitiveLiteral.getTy(TyPrimitiveKind.String, fieldName), context, false, false, deep)
            } else null
        }

        fun findMethod(cls: ITyClass, memberName: String, context: SearchContext, deep: Boolean = true): LuaClassMethod<*>? {
            var target: LuaClassMethod<*>? = null
            processMember(cls, memberName, context, { member, _ ->
                if (member is LuaClassMethod<*>) {
                    target = member
                    false
                } else {
                    true
                }
            }, deep)
            return target
        }

        fun processAllIndexers(type: ITyClass, context: SearchContext, process: ProcessClassMember, deep: Boolean = true): Boolean {
            return processClassKey(type, "[]", context, process, deep)
        }

        fun processIndexer(type: ITyClass, indexTy: ITy, exact: Boolean, context: SearchContext, process: ProcessClassMember, deep: Boolean = true): Boolean {
            val exactIndexerKey = "*[${indexTy.displayName}]"
            var exactIndexerFound = false

            val exactIndexerResult = processClassKey(type, exactIndexerKey, context, { member, ownerTy ->
                exactIndexerFound = true
                process(member, ownerTy)
            }, deep)

            if (exactIndexerFound || exact) {
                return exactIndexerResult
            }

            var inexactIndexerTy: ITy? = null

            processAllIndexers(type, context, { member, _ ->
                val candidateIndexerTy = member.guessIndexType(context)

                if (candidateIndexerTy?.contravariantOf(indexTy, context, TyVarianceFlags.STRICT_UNKNOWN) == true) {
                    if (inexactIndexerTy?.contravariantOf(candidateIndexerTy, context, TyVarianceFlags.STRICT_UNKNOWN) != false) {
                        inexactIndexerTy = candidateIndexerTy
                    }
                }

                true
            }, deep)

            return inexactIndexerTy?.let {
                processClassKey(type, "*[${it.displayName}]", context, process, deep)
            } ?: false
        }

        fun findIndexer(type: ITyClass, indexTy: ITy, context: SearchContext, exact: Boolean = false, searchMembers: Boolean = true, deep: Boolean = true): LuaClassMember? {
            if (searchMembers && indexTy is TyPrimitiveLiteral && indexTy.primitiveKind == TyPrimitiveKind.String) {
                findMember(type, indexTy.value, context, false, deep)?.let {
                    return it
                }
            }

            var target: LuaClassMember? = null

            processIndexer(type, indexTy, exact, context, { member, _ ->
                target = member
                false
            }, deep)

            return target
        }

        fun processAll(type: ITyClass, context: SearchContext, process: ProcessClassMember) {
            if (processKey(type, type.className, context, process)) {
                type.lazyInit(context)
                type.processAlias({ aliasedName ->
                    LuaClassIndex.find(aliasedName, context)?.type?.let { aliasedClass ->
                        processKey(aliasedClass, aliasedName, context, process)
                    } ?: true
                })
            }
        }

        fun processNamespaceMember(namespace: String, memberName: String, context: SearchContext, processor: Processor<LuaClassMember>): Boolean {
            if (context.isDumb) {
                return false
            }

            val key = "$namespace*$memberName"

            val members = LuaClassMemberIndex.instance.get(key.hashCode(), context.project, context.scope)
            return ContainerUtil.process(members, processor)
        }

        fun indexMemberStub(indexSink: IndexSink, className: String, memberName: String) {
            indexSink.occurrence(StubKeys.CLASS_MEMBER, className.hashCode())
            indexSink.occurrence(StubKeys.CLASS_MEMBER, "$className*$memberName".hashCode())
        }

        fun indexIndexerStub(indexSink: IndexSink, className: String, indexTy: ITy) {
            TyUnion.each(indexTy) {
                if (it is TyPrimitiveLiteral && it.primitiveKind == TyPrimitiveKind.String) {
                    indexMemberStub(indexSink, className, it.value)
                } else {
                    indexMemberStub(indexSink, className, "[${it.displayName}]")
                    indexSink.occurrence(StubKeys.CLASS_MEMBER, "$className[]".hashCode())
                }
            }
        }
    }
}
