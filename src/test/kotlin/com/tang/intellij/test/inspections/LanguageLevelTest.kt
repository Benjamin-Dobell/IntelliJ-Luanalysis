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

package com.tang.intellij.test.inspections

import com.tang.intellij.lua.codeInsight.inspection.LanguageLevelInspection
import com.tang.intellij.lua.lang.LuaLanguageLevel
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.project.StdLibraryProvider

class LanguageLevelTest : LuaInspectionsTestBase(LanguageLevelInspection()) {

    fun testDisallowLua53Features() {
        val defaultLanguageLevel = LuaSettings.instance.languageLevel
        LuaSettings.instance.languageLevel = LuaLanguageLevel.LUA52
        StdLibraryProvider.reload()

        try {
            checkByText("""
            local a = 1
            a = a <error descr="The binary operator '&' only available in Lua 5.3 and above">&</error> a
            a = a <error descr="The binary operator '|' only available in Lua 5.3 and above">|</error> a
            a = a <error descr="The binary operator '~' only available in Lua 5.3 and above">~</error> a
            a = a <error descr="The binary operator '>>' only available in Lua 5.3 and above">>></error> a
            a = a <error descr="The binary operator '<<' only available in Lua 5.3 and above"><<</error> a
            a = <error descr="The unary operator '~' only available in Lua 5.3 and above">~</error>a
        """.trimIndent())
        } finally {
            LuaSettings.instance.languageLevel = defaultLanguageLevel
            StdLibraryProvider.reload()
        }
    }

    fun testAllowLua53Features() {
        val defaultLanguageLevel = LuaSettings.instance.languageLevel
        LuaSettings.instance.languageLevel = LuaLanguageLevel.LUA53
        StdLibraryProvider.reload()

        try {
            checkByText("""
            local a = 1
            a = a & a
            a = a | a
            a = a ~ a
            a = a >> a
            a = a << a
            a = ~a
        """.trimIndent())
        } finally {
            LuaSettings.instance.languageLevel = defaultLanguageLevel
            StdLibraryProvider.reload()
        }
    }

    fun testDisallowLua54Features() {
        val defaultLanguageLevel = LuaSettings.instance.languageLevel
        LuaSettings.instance.languageLevel = LuaLanguageLevel.LUA53
        StdLibraryProvider.reload()

        try {
            checkByText("""
            local constVal <error><const></error> = 1
            local closedValue <error><close></error> = 1
        """.trimIndent())
        } finally {
            LuaSettings.instance.languageLevel = defaultLanguageLevel
            StdLibraryProvider.reload()
        }
    }

    fun testAllowLua54Features() {
        val defaultLanguageLevel = LuaSettings.instance.languageLevel
        LuaSettings.instance.languageLevel = LuaLanguageLevel.LUA54
        StdLibraryProvider.reload()

        try {
            checkByText("""
            local constVal <const> = 1
            local closedValue <close> = 1
        """.trimIndent())
        } finally {
            LuaSettings.instance.languageLevel = defaultLanguageLevel
            StdLibraryProvider.reload()
        }
    }
}
