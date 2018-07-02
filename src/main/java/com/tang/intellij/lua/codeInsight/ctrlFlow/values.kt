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

package com.tang.intellij.lua.codeInsight.ctrlFlow

import com.intellij.util.ThreeState
import com.tang.intellij.lua.psi.LuaExpr
import com.tang.intellij.lua.psi.LuaLiteralExpr
import com.tang.intellij.lua.psi.LuaNameDef

abstract class VMValue

interface VMValueFactory {
    fun createValue(expr: LuaExpr): VMValue

    fun createVariableValue(def: LuaNameDef): VMVariableValue

    fun createLiteralValue(expr: LuaLiteralExpr): VMValue
}

object VMUnknown : VMValue()

abstract class VMConstantValue : VMValue()

object VMNil : VMConstantValue()

class VMBoolean(val value: ThreeState) : VMConstantValue() {
    companion object {
        val TRUE = VMBoolean(ThreeState.YES)
        val FALSE = VMBoolean(ThreeState.NO)
        val UNSURE = VMBoolean(ThreeState.UNSURE)
    }
}

class VMString(val value: String) : VMConstantValue()

class VMNumber(val value: Float) : VMConstantValue()

class VMFunction : VMConstantValue()

class VMVariableValue(val name: String) : VMValue()