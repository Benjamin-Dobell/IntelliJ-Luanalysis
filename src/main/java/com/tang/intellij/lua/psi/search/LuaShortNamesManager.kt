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

abstract class LuaShortNamesManager {
    companion object {
        val EP_NAME = ExtensionPointName.create<LuaShortNamesManager>("com.tang.intellij.lua.luaShortNamesManager")

        private val KEY = Key.create<LuaShortNamesManager>("com.tang.intellij.lua.luaShortNamesManager")

        fun getInstance(project: Project): LuaShortNamesManager {
            var instance = project.getUserData(KEY)
            if (instance == null) {
                instance = CompositeLuaShortNamesManager()
                project.putUserData(KEY, instance)
            }
            return instance
        }
    }

    abstract fun findAlias(name: String, context: SearchContext): LuaTypeAlias?

    abstract fun findClass(name: String, context: SearchContext): LuaClass?

    abstract fun findMember(type: ITyClass, fieldName: String, context: SearchContext): LuaClassMember?

    abstract fun findIndexer(type: ITyClass, indexTy: ITy, context: SearchContext, exact: Boolean = false): LuaClassMember?

    abstract fun processAllAliases(project: Project, processor: Processor<String>): Boolean

    abstract fun processAllClasses(project: Project, processor: Processor<String>): Boolean

    abstract fun processAliases(name: String, context: SearchContext, processor: Processor<in LuaTypeAlias>): Boolean

    abstract fun processClasses(name: String, context: SearchContext, processor: Processor<in LuaClass>): Boolean

    abstract fun getClassMembers(clazzName: String, context: SearchContext): Collection<LuaClassMember>

    abstract fun processMember(type: ITyClass, fieldName: String, context: SearchContext, processor: Processor<in LuaClassMember>): Boolean

    abstract fun processIndexer(type: ITyClass, indexTy: ITy, exact: Boolean, context: SearchContext, processor: Processor<in LuaClassMember>): Boolean

    open fun findType(name: String, context: SearchContext): LuaTypeDef? {
        return findClass(name, context) ?: findAlias(name, context)
    }

    open fun processTypes(name: String, context: SearchContext, processor: Processor<in LuaTypeDef>): Boolean {
        return processClasses(name, context, processor) && processAliases(name, context, processor)
    }
}
