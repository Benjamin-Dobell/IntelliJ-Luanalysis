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

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.tree.TreeUtil
import com.tang.intellij.lua.codeInsight.inspection.*
import com.tang.intellij.lua.codeInsight.inspection.doc.GenericConstraintInspection
import com.tang.intellij.lua.codeInsight.inspection.doc.GenericParameterShadowed
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.test.inspections.LuaInspectionsTestBase
import org.intellij.lang.annotations.Language

class Issues : LuaInspectionsTestBase(
    AssignTypeInspection(),
    GenericConstraintInspection(),
    GenericParameterShadowed(),
    IllegalOverrideInspection(),
    MatchFunctionSignatureInspection(),
    ReturnTypeInspection(),
    UndeclaredMemberInspection(),
    UndeclaredVariableInspection()
) {
    fun check(@Language("Lua") text: String, checkWarn: Boolean = true, checkInfo: Boolean = false, checkWeakWarn: Boolean = false) {
        LuaSettings.instance.isNilStrict = true
        LuaSettings.instance.isUnknownCallable = false
        LuaSettings.instance.isUnknownIndexable = false
        checkByText(text, checkWarn, checkInfo, checkWeakWarn)
        LuaSettings.instance.isNilStrict = false
        LuaSettings.instance.isUnknownCallable = true
        LuaSettings.instance.isUnknownIndexable = true
    }

    // https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/pull/54
    fun test54() {
        val code = """
            function foo()
                local test = {
                    fun = function()
                        if true then<caret>
                    end
                }
            end
        """.trimIndent()

        val expected = """
            function foo()
                local test = {
                    fun = function()
                        if true then
                            
                        end
                    end
                }
            end
        """.trimIndent()

        myFixture.configureByText("main.lua", code)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
        myFixture.checkResult(expected)
    }

    // https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/81
    fun test81() {
        check(
            """
            local function file_contains(filename, text)
              local f = assert(io.open(filename, "r"))
              local found = f:read("a"):find(text, 1, true)
              f:close()
              return found
            end
        """.trimIndent()
        )
    }

    // https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/82
    fun test82() {
        check(
            """
            function test(arg) end

            function issue82(...)
                local t = ...
                test(t)
            end
        """.trimIndent()
        )
    }

    // https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/84
    fun test84() {
        check(
            """
            ---@type fun<T>(array: T[], index: number)
            local fun1
            ---@type fun<T>(array: T[], index: number)
            local fun2
            
            --- @generic T
            --- @param array T[]
            --- @param index number
            fun1 = function(array, index)
                -- Needs to call fun2
            end
            
            --- @generic T
            --- @param array T[]
            --- @param index number
            fun2 = function(array, index)
                -- Needs to call fun1
            end
        """.trimIndent()
        )
    }

    // https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/85
    fun test85() {
        check(
            """
            ---@alias set<x> table<x,any>

            ---@type set<set<number>>
            local aSet

            aSet = aSet
        """.trimIndent()
        )
    }

    // https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/87
    fun test87() {
        check(
            """
            ---@class MyClass
            ---@overload fun(arg: {number, number})
            MyClass = {}
            """.trimIndent()
        )
    }
}
