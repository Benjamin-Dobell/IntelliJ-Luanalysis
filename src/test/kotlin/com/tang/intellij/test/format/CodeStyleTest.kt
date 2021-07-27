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

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.tang.intellij.lua.editor.formatter.LuaCodeStyleSettings
import com.tang.intellij.test.LuaTestBase
import org.intellij.lang.annotations.Language

class CodeStyleTest : LuaTestBase() {
    fun doFormatted(@Language("Lua") unformatted: String, @Language("Lua") formatted: String, action: (common: CommonCodeStyleSettings, lua: LuaCodeStyleSettings, format: () -> Unit) -> Unit) {
        myFixture.configureByText("main.lua", unformatted)

        val common = CodeStyle.getLanguageSettings(myFixture.file)
        val lua = CodeStyle.getCustomSettings(myFixture.file, LuaCodeStyleSettings::class.java)

        action(common, lua) {
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT)
        }

        myFixture.checkResult(formatted)
    }

    fun doUnformatted(@Language("Lua") lua: String, action: (common: CommonCodeStyleSettings, lua: LuaCodeStyleSettings, format: () -> Unit) -> Unit) {
        doFormatted(lua, lua, action)
    }

    fun doDefaultFormatted(@Language("Lua") unformatted: String, @Language("Lua") formatted: String) {
        doFormatted(unformatted, formatted)  { _, _, format ->
            format()
        }
    }

    fun doDefaultUnformatted(@Language("Lua") lua: String) {
        doUnformatted(lua)  { _, _, format ->
            format()
        }
    }

    fun `test format multiple statements in one lines`() {
        doDefaultFormatted("local a = 1 local b = 2", """
            local a = 1
            local b = 2
        """.trimIndent())
    }

    fun `test keep multiple statements in one line`() {
        doUnformatted("local a = 1 local b = 2") { common, lua, format ->
            val previous = lua.KEEP_MULTIPLE_STATEMENTS_IN_ONE_LINE
            lua.KEEP_MULTIPLE_STATEMENTS_IN_ONE_LINE = true

            format()

            lua.KEEP_MULTIPLE_STATEMENTS_IN_ONE_LINE = previous
        }
    }
}
