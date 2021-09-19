/*
 * Copyright (c) 2021
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

import com.tang.intellij.lua.comment.psi.LuaDocTy
import com.tang.intellij.lua.psi.Visibility
import com.tang.intellij.lua.search.SearchContext

interface TypeMember : TypeGuessable {
    fun guessParentType(context: SearchContext): ITy

    fun guessIndexType(context: SearchContext): ITy? {
        return indexType?.getType()
    }

    // LuaPsiElement/NavigatablePsiElement have getName() and we want implementors to be able to implement these
    // interfaces as well as TypeMember, so our getter is explicitly given the JVM name "getName". Unfortunately,
    // Kotlin tooling is unfortunately unaware of our fix and reports CONFLICTING_INHERITED_JVM_DECLARATIONS.
    val name: String?
        @get:JvmName("getName") get

    val visibility: Visibility
    val isDeprecated: Boolean
    val isExplicitlyTyped: Boolean

    val indexType: LuaDocTy?
        get() = null
}

fun TypeMember.guessParentClass(context: SearchContext): ITyClass? {
    val ty = guessParentType(context)
    return TyUnion.getPerfectClass(ty)
}
