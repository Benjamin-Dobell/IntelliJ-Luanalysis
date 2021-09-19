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

import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import com.tang.intellij.lua.psi.LuaTypeMethod
import com.tang.intellij.lua.ty.guessParentClass
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassMemberIndex
import com.tang.intellij.lua.ty.ITyClass
import com.tang.intellij.lua.ty.ITyGeneric
import com.tang.intellij.lua.ty.Ty

class LuaOverridenMethodsSearchExecutor : QueryExecutor<LuaTypeMethod<*>, LuaOverridenMethodsSearch.SearchParameters> {
    override fun execute(searchParameters: LuaOverridenMethodsSearch.SearchParameters, processor: Processor<in LuaTypeMethod<*>>): Boolean {
        val method = searchParameters.method
        val project = method.project
        val context = SearchContext.get(project)
        val type = method.guessParentClass(context)
        val methodName = method.name
        if (type != null && methodName != null) {
            Ty.processSuperClasses(context, type) { superType->
                ProgressManager.checkCanceled()
                val superClass = (if (superType is ITyGeneric) superType.base else superType) as? ITyClass
                if (superClass != null) {
                    val superMethod = LuaClassMemberIndex.findMethod(context, superClass, methodName)
                    if (superMethod == null) true else processor.process(superMethod)
                } else true
            }
        }
        return false
    }
}
