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

import com.tang.intellij.lua.psi.*

open class VMValueFactoryImpl : VMValueFactory {
    override fun createValue(expr: LuaExpr): VMValue {
        return VMUnknown
    }

    override fun createVariableValue(def: LuaNameDef): VMVariableValue {
        return VMVariableValue(def.name)
    }

    override fun createLiteralValue(expr: LuaLiteralExpr) = when (expr.kind) {
        LuaLiteralKind.Bool -> if (expr.boolValue) VMBoolean.TRUE else VMBoolean.FALSE
        LuaLiteralKind.Nil -> VMNil
        LuaLiteralKind.Number -> VMNumber(expr.numberValue)
        LuaLiteralKind.String -> VMString(expr.stringValue)
        else -> VMUnknown
    }
}