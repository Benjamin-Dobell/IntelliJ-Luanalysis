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
import com.tang.intellij.lua.psi.LuaClassMember
import com.tang.intellij.lua.psi.LuaTypeAlias
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaAliasIndex
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.stubs.index.LuaClassMemberIndex
import com.tang.intellij.lua.ty.ITy
import com.tang.intellij.lua.ty.ITyClass

class LuaShortNamesManagerImpl : LuaShortNamesManager {

    override fun findAlias(name: String, context: SearchContext): LuaTypeAlias? {
        return LuaAliasIndex.find(name, context)
    }

    override fun findClass(name: String, context: SearchContext): LuaClass? {
        return LuaClassIndex.find(name, context)
    }

    override fun findMember(type: ITyClass, fieldName: String, context: SearchContext): LuaClassMember? {
        return LuaClassMemberIndex.findMember(type, fieldName, context)
    }

    override fun findIndexer(type: ITyClass, indexTy: ITy, context: SearchContext, exact: Boolean): LuaClassMember? {
        return LuaClassMemberIndex.findIndexer(type, indexTy, context, exact)
    }

    override fun processAllAliases(project: Project, processor: Processor<String>): Boolean {
        return LuaAliasIndex.instance.processAllKeys(project, processor)
    }

    override fun processAliases(name: String, context: SearchContext, processor: Processor<in LuaTypeAlias>): Boolean {
        return ContainerUtil.process(LuaAliasIndex.instance.get(name, context.project, context.scope), processor)
    }

    override fun processAllClasses(project: Project, processor: Processor<String>): Boolean {
        return LuaClassIndex.processKeys(project, processor)
    }

    override fun processClasses(name: String, context: SearchContext, processor: Processor<in LuaClass>): Boolean {
        return LuaClassIndex.process(name, context.project, context.scope, Processor { processor.process(it) })
    }

    override fun getClassMembers(clazzName: String, context: SearchContext): Collection<LuaClassMember> {
        return LuaClassMemberIndex.getMembers(clazzName, context)
    }

    override fun processMember(type: ITyClass, fieldName: String, context: SearchContext, processor: Processor<in LuaClassMember>): Boolean {
        return LuaClassMemberIndex.processMember(type, fieldName, context, processor)
    }

    override fun processIndexer(type: ITyClass, indexTy: ITy, exact: Boolean, context: SearchContext, processor: Processor<in LuaClassMember>): Boolean {
        return LuaClassMemberIndex.processIndexer(type, indexTy, exact, context, processor)
    }
}
