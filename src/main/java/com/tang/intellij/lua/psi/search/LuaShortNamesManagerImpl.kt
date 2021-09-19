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

package com.tang.intellij.lua.psi.search

import com.intellij.openapi.project.Project
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import com.tang.intellij.lua.psi.LuaClass
import com.tang.intellij.lua.psi.LuaPsiTypeMember
import com.tang.intellij.lua.psi.LuaTypeAlias
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaAliasIndex
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.stubs.index.LuaClassMemberIndex
import com.tang.intellij.lua.stubs.index.ProcessLuaPsiClassMember
import com.tang.intellij.lua.ty.ITy
import com.tang.intellij.lua.ty.ITyClass

class LuaShortNamesManagerImpl : LuaShortNamesManager {

    override fun findAlias(context: SearchContext, name: String): LuaTypeAlias? {
        return LuaAliasIndex.find(context, name)
    }

    override fun findClass(context: SearchContext, name: String): LuaClass? {
        return LuaClassIndex.find(context, name)
    }

    override fun processAllAliases(project: Project, processor: Processor<String>): Boolean {
        return LuaAliasIndex.instance.processAllKeys(project, processor)
    }

    override fun processAliases(context: SearchContext, name: String, processor: Processor<in LuaTypeAlias>): Boolean {
        return ContainerUtil.process(LuaAliasIndex.instance.get(name, context.project, context.scope), processor)
    }

    override fun processAllClasses(project: Project, processor: Processor<String>): Boolean {
        return LuaClassIndex.processKeys(project, processor)
    }

    override fun processClasses(context: SearchContext, name: String, processor: Processor<in LuaClass>): Boolean {
        return LuaClassIndex.process(name, context.project, context.scope, { processor.process(it) })
    }

    override fun getClassMembers(context: SearchContext, clazzName: String): Collection<LuaPsiTypeMember> {
        return LuaClassMemberIndex.getMembers(context, clazzName)
    }

    override fun processMember(
        context: SearchContext,
        type: ITyClass,
        fieldName: String,
        searchIndexers: Boolean,
        deep: Boolean,
        process: ProcessLuaPsiClassMember
    ): Boolean {
        return LuaClassMemberIndex.processMember(context, type, fieldName, searchIndexers, deep, process)
    }

    override fun processIndexer(
        context: SearchContext,
        type: ITyClass,
        indexTy: ITy,
        exact: Boolean,
        searchMembers: Boolean,
        deep: Boolean,
        process: ProcessLuaPsiClassMember
    ): Boolean {
        return LuaClassMemberIndex.processIndexer(context, type, indexTy, exact, searchMembers, deep, process)
    }
}
