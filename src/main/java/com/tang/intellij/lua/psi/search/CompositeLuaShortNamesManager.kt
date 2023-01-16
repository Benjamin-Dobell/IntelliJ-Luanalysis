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

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.Processor
import com.tang.intellij.lua.psi.LuaClass
import com.tang.intellij.lua.psi.LuaPsiTypeMember
import com.tang.intellij.lua.psi.LuaTypeAlias
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.ProcessLuaPsiClassMember
import com.tang.intellij.lua.ty.ITy
import com.tang.intellij.lua.ty.ITyClass
import com.tang.intellij.lua.ty.ITySubstitutor

class CompositeLuaShortNamesManager : LuaShortNamesManager {
    override fun findAlias(context: SearchContext, name: String): LuaTypeAlias? {
        for (manager in EP_NAME.extensionList) {
            val alias = manager.findAlias(context, name)
            if (alias != null)
                return alias
        }
        return null
    }

    override fun findClass(context: SearchContext, name: String): LuaClass? {
        for (manager in EP_NAME.extensionList) {
            val c = manager.findClass(context, name)
            if (c != null)
                return c
        }
        return null
    }

    override fun processAllAliases(project: Project, processor: Processor<String>): Boolean {
        for (manager in EP_NAME.extensionList) {
            if (!manager.processAllAliases(project, processor))
                return false
        }
        return true
    }

    override fun processAliases(context: SearchContext, name: String, processor: Processor<in LuaTypeAlias>): Boolean {
        for (manager in EP_NAME.extensionList) {
            if (!manager.processAliases(context, name, processor))
                return false
        }
        return true
    }

    override fun processAllClasses(project: Project, processor: Processor<String>): Boolean {
        for (manager in EP_NAME.extensionList) {
            if (!manager.processAllClasses(project, processor))
                return false
        }
        return true
    }

    override fun processClasses(context: SearchContext, name: String, processor: Processor<in LuaClass>): Boolean {
        for (manager in EP_NAME.extensionList) {
            if (!manager.processClasses(context, name, processor))
                return false
        }
        return true
    }

    override fun getClassMembers(context: SearchContext, clazzName: String): Collection<LuaPsiTypeMember> {
        val collection = mutableListOf<LuaPsiTypeMember>()
        for (manager in EP_NAME.extensionList) {
            val col = manager.getClassMembers(context, clazzName)
            collection.addAll(col)
        }
        return collection
    }

    override fun processMember(context: SearchContext, type: ITyClass, fieldName: String, searchIndexers: Boolean, deep: Boolean, indexerSubstitutor: ITySubstitutor?, process: ProcessLuaPsiClassMember): Boolean {
        for (manager in EP_NAME.extensionList) {
            if (!manager.processMember(context, type, fieldName, searchIndexers, deep, indexerSubstitutor, process))
                return false
        }
        return true
    }

    override fun processIndexer(
            context: SearchContext,
            type: ITyClass,
            indexTy: ITy,
            exact: Boolean,
            searchMembers: Boolean,
            deep: Boolean,
            indexerSubstitutor: ITySubstitutor?,
            process: ProcessLuaPsiClassMember
    ): Boolean {
        for (manager in EP_NAME.extensionList) {
            if (!manager.processIndexer(context, type, indexTy, exact, searchMembers, deep, indexerSubstitutor, process))
                return false
        }
        return true
    }

    companion object {
        private val EP_NAME = ExtensionPointName.create<LuaShortNamesManager>("au.com.glassechidna.luanalysis.luaShortNamesManager")
    }
}
