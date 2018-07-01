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
import com.tang.intellij.lua.psi.LuaNameDef

abstract class VMValue {
    companion object {
        val BOOLEAN_TRUE = VMBoolean(ThreeState.YES)
        val BOOLEAN_FALSE = VMBoolean(ThreeState.NO)
        val BOOLEAN_UNSURE = VMBoolean(ThreeState.UNSURE)
    }
}

interface VMValueFactory {
    fun createValue(expr: LuaExpr): VMValue

    fun createVariableValue(def: LuaNameDef): VMVariableValue
}

object VMUnknown : VMValue()

abstract class VMConstantValue : VMValue()

class VMBoolean(val value: ThreeState) : VMConstantValue()

class VMString : VMConstantValue()

class VMNumber : VMConstantValue()

class VMFunction : VMConstantValue()

class VMVariableValue : VMValue()