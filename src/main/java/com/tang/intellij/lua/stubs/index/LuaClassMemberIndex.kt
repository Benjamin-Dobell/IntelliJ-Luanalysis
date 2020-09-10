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
import com.tang.intellij.lua.comment.psi.LuaDocTagField
import com.tang.intellij.lua.psi.LuaClassMember
import com.tang.intellij.lua.psi.LuaClassMethod
import com.tang.intellij.lua.psi.LuaTableField
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*

fun assertNotCreatingStub() {
    Thread.currentThread().getStackTrace().forEach {
        assert(!it.methodName.contains("createStub")) { "Illegal attempt to access an index whilst indexing" }
    }
}

class LuaClassMemberIndex : IntStubIndexExtension<LuaClassMember>() {
    override fun getKey() = StubKeys.CLASS_MEMBER

    override fun get(s: Int, project: Project, scope: GlobalSearchScope): Collection<LuaClassMember> =
            StubIndex.getElements(StubKeys.CLASS_MEMBER, s, project, scope, LuaClassMember::class.java)

    companion object {
        val instance = LuaClassMemberIndex()

        private fun processKey(key: String, context: SearchContext, processor: Processor<in LuaClassMember>): Boolean {
            if (context.isDumb)
                return false

            assertNotCreatingStub()

            val all = LuaClassMemberIndex.instance.get(key.hashCode(), context.project, context.scope)
            return ContainerUtil.process(all, processor)
        }

        private fun processClassKey(cls: ITyClass?, className: String, key: String, context: SearchContext, processor: Processor<in LuaClassMember>, deep: Boolean): Boolean {
            val classKey = "$className$key"

            if (!processKey(classKey, context, processor))
                return false

            if (deep) {
                val type = cls ?: LuaClassIndex.find(className, context)?.type

                if (type != null) {
                    // from alias
                    type.lazyInit(context)
                    val notFound = type.processAlias(Processor {
                        processClassKey(null, it, key, context, processor, deep)
                    })
                    if (!notFound)
                        return false

                    return Ty.processSuperClasses(type, context) { superType ->
                        val superClass = (if (superType is ITyGeneric) superType.base else superType) as? ITyClass
                        if (superClass != null) {
                            processClassKey(superClass, superClass.className, key, context, processor, false)
                        } else true
                    }
                }
            }
            return true
        }

        private fun processClassKey(cls: ITyClass, key: String, context: SearchContext, processor: Processor<in LuaClassMember>, deep: Boolean = true): Boolean {
            return if (cls is TyParameter)
                (cls.superClass as? ITyClass)?.let { processClassKey(it, it.className, key, context, processor, deep) } ?: true
            else processClassKey(cls, cls.className, key, context, processor, deep)
        }

        fun processMember(cls: ITyClass, fieldName: String, context: SearchContext, processor: Processor<in LuaClassMember>, deep: Boolean = true): Boolean {
            return processClassKey(cls, "*$fieldName", context, processor, deep)
        }

        fun processMember(className: String, fieldName: String, context: SearchContext, processor: Processor<in LuaClassMember>, deep: Boolean = true): Boolean {
            return processClassKey(null, className, "*$fieldName", context, processor, deep)
        }

        fun findMember(type: ITyClass, fieldName: String, context: SearchContext, searchIndexers: Boolean = true, deep: Boolean = true): LuaClassMember? {
            var perfect: LuaClassMember? = null
            var tagField: LuaDocTagField? = null
            var tableField: LuaTableField? = null
            processMember(type, fieldName, context, Processor {
                when (it) {
                    is LuaDocTagField -> {
                        tagField = it
                        false
                    }
                    is LuaTableField -> {
                        tableField = it
                        true
                    }
                    else -> {
                        if (perfect == null)
                            perfect = it
                        true
                    }
                }
            }, deep)

            if (tagField != null) return tagField
            if (tableField != null) return tableField
            if (perfect != null) return perfect

            return if (searchIndexers) {
                findIndexer(type, TyPrimitiveLiteral.getTy(TyPrimitiveKind.String, fieldName), context, false, false, deep)
            } else null
        }

        fun findMethod(className: String, memberName: String, context: SearchContext, deep: Boolean = true): LuaClassMethod? {
            var target: LuaClassMethod? = null
            processMember(className, memberName, context, Processor {
                if (it is LuaClassMethod) {
                    target = it
                    return@Processor false
                }
                true
            }, deep)
            return target
        }

        fun processAllIndexers(type: ITyClass, context: SearchContext, processor: Processor<in LuaClassMember>, deep: Boolean = true): Boolean {
            return processClassKey(type, "[]", context, processor, deep)
        }

        fun processIndexer(type: ITyClass, indexTy: ITy, exact: Boolean, context: SearchContext, processor: Processor<in LuaClassMember>, deep: Boolean = true): Boolean {
            val exactIndexerKey = "*[${indexTy.displayName}]"
            var exactIndexerFound = false

            val exactIndexerResult = processClassKey(type, exactIndexerKey, context, Processor {
                exactIndexerFound = true
                processor.process(it)
            }, deep)

            if (exactIndexerFound || exact) {
                return exactIndexerResult
            }

            var inexactIndexerTy: ITy? = null

            processAllIndexers(type, context, Processor {
                val candidateIndexerTy = it.guessIndexType(context)

                if (candidateIndexerTy?.contravariantOf(indexTy, context, TyVarianceFlags.STRICT_UNKNOWN) == true) {
                    if (inexactIndexerTy?.contravariantOf(candidateIndexerTy, context, TyVarianceFlags.STRICT_UNKNOWN) != false) {
                        inexactIndexerTy = candidateIndexerTy
                    }
                }

                true
            }, deep)

            return inexactIndexerTy?.let {
                processClassKey(type, "*[${it.displayName}]", context, processor, deep)
            } ?: false
        }

        fun findIndexer(type: ITyClass, indexTy: ITy, context: SearchContext, exact: Boolean = false, searchMembers: Boolean = true, deep: Boolean = true): LuaClassMember? {
            if (searchMembers && indexTy is TyPrimitiveLiteral && indexTy.primitiveKind == TyPrimitiveKind.String) {
                findMember(type, indexTy.value, context, false, deep)?.let {
                    return it
                }
            }

            var target: LuaClassMember? = null

            processIndexer(type, indexTy, exact, context, Processor {
                target = it
                false
            }, deep)

            return target
        }

        fun processAll(type: ITyClass, context: SearchContext, processor: Processor<in LuaClassMember>) {
            if (processKey(type.className, context, processor)) {
                type.lazyInit(context)
                type.processAlias(Processor {
                    processKey(it, context, processor)
                })
            }
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
