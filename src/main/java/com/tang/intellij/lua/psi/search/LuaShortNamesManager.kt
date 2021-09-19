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
import com.intellij.openapi.util.Key
import com.intellij.util.Processor
import com.tang.intellij.lua.psi.LuaClass
import com.tang.intellij.lua.psi.LuaPsiTypeMember
import com.tang.intellij.lua.psi.LuaTypeAlias
import com.tang.intellij.lua.psi.LuaTypeDef
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.ProcessLuaPsiClassMember
import com.tang.intellij.lua.ty.ITy
import com.tang.intellij.lua.ty.ITyClass

interface LuaShortNamesManager {
    companion object {
        private val KEY = Key.create<LuaShortNamesManager>("au.com.glassechidna.luanalysis.luaShortNamesManager")

        fun getInstance(project: Project): LuaShortNamesManager {
            var instance = project.getUserData(KEY)
            if (instance == null) {
                instance = CompositeLuaShortNamesManager()
                project.putUserData(KEY, instance)
            }
            return instance
        }
    }

    fun findAlias(context: SearchContext, name: String): LuaTypeAlias?

    fun findClass(context: SearchContext, name: String): LuaClass?

    fun processAllAliases(project: Project, processor: Processor<String>): Boolean

    fun processAllClasses(project: Project, processor: Processor<String>): Boolean

    fun processAliases(context: SearchContext, name: String, processor: Processor<in LuaTypeAlias>): Boolean

    fun processClasses(context: SearchContext, name: String, processor: Processor<in LuaClass>): Boolean

    fun getClassMembers(context: SearchContext, clazzName: String): Collection<LuaPsiTypeMember>

    fun processMember(context: SearchContext, type: ITyClass, fieldName: String, searchIndexers: Boolean, deep: Boolean, process: ProcessLuaPsiClassMember): Boolean

    fun processIndexer(context: SearchContext, type: ITyClass, indexTy: ITy, exact: Boolean, searchMembers: Boolean, deep: Boolean, process: ProcessLuaPsiClassMember): Boolean

    fun findType(context: SearchContext, name: String): LuaTypeDef? {
        return findClass(context, name) ?: findAlias(context, name)
    }

    fun processTypes(context: SearchContext, name: String, processor: Processor<in LuaTypeDef>): Boolean {
        return processClasses(context, name, processor) && processAliases(context, name, processor)
    }
}
