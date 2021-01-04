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
import com.intellij.openapi.util.Key
import com.intellij.util.Processor
import com.tang.intellij.lua.psi.LuaClass
import com.tang.intellij.lua.psi.LuaClassMember
import com.tang.intellij.lua.psi.LuaTypeAlias
import com.tang.intellij.lua.psi.LuaTypeDef
import com.tang.intellij.lua.search.SearchContext
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

    fun findAlias(name: String, context: SearchContext): LuaTypeAlias?

    fun findClass(name: String, context: SearchContext): LuaClass?

    fun findMember(type: ITyClass, fieldName: String, context: SearchContext): LuaClassMember?

    fun findIndexer(type: ITyClass, indexTy: ITy, context: SearchContext, exact: Boolean = false): LuaClassMember?

    fun processAllAliases(project: Project, processor: Processor<String>): Boolean

    fun processAllClasses(project: Project, processor: Processor<String>): Boolean

    fun processAliases(name: String, context: SearchContext, processor: Processor<in LuaTypeAlias>): Boolean

    fun processClasses(name: String, context: SearchContext, processor: Processor<in LuaClass>): Boolean

    fun getClassMembers(clazzName: String, context: SearchContext): Collection<LuaClassMember>

    fun processMember(type: ITyClass, fieldName: String, context: SearchContext, processor: Processor<in LuaClassMember>): Boolean

    fun processIndexer(type: ITyClass, indexTy: ITy, exact: Boolean, context: SearchContext, processor: Processor<in LuaClassMember>): Boolean

    fun findType(name: String, context: SearchContext): LuaTypeDef? {
        return findClass(name, context) ?: findAlias(name, context)
    }

    fun processTypes(name: String, context: SearchContext, processor: Processor<in LuaTypeDef>): Boolean {
        return processClasses(name, context, processor) && processAliases(name, context, processor)
    }
}
