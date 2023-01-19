/*
 * Copyright (c) 2020
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

package com.tang.intellij.lua.search

import com.google.common.collect.Sets
import com.intellij.psi.PsiElement
import com.tang.intellij.lua.psi.LuaNameExpr


private val globalSearchGuardSet = ThreadLocal.withInitial { mutableSetOf<String>() }
private val recursionGuardSets = ThreadLocal.withInitial { mutableMapOf<String, MutableSet<PsiElement>>() }

fun <T>withSearchGuard(psi: LuaNameExpr, action: () -> T?): T? {
    val name = psi.name
    val guardSet = globalSearchGuardSet.get()

    if (!guardSet.add(name)) {
        return null
    }

    try {
        return action()
    } finally {
        guardSet.remove(name)
    }
}

fun <T>withRecursionGuard(name: String, psi: PsiElement, dumb: Boolean = false, action: () -> T?): T? {
    // When trying to infer types in "smart" mode, IntelliJ may detect file changes *then* proceed to reindex those files i.e. enter dumb mode. When we've
    // entered dumb mode, we basically want to disregard any recursion detection that was taking place in smart mode, so we maintain a separate guard set.
    val guardName = if (dumb) name + "Dumb" else name
    val guardSet = recursionGuardSets.get().getOrPut(guardName) {
        Sets.newIdentityHashSet()
    }

    if (!guardSet.add(psi)) {
        return null
    }

    try {
        return action()
    } finally {
        guardSet.remove(psi)
    }
}
