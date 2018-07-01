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

package com.tang.intellij.lua.codeInsight.ctrlFlow.instructions

import com.tang.intellij.lua.codeInsight.ctrlFlow.*
import com.tang.intellij.lua.psi.LuaBinaryExpr
import com.tang.intellij.lua.psi.LuaUnaryExpr

open class VMInstructionImpl : VMInstruction {

    override var index: Int = 0

    override lateinit var owner: VMPseudoCode

    override lateinit var scope: VMScope

    override fun accept(visitor: InstructionVisitor) {
    }
}

class GotoInstruction(val label: VMLabel) : VMInstructionImpl()

class ConditionGotoInstruction(val label: VMLabel) : VMInstructionImpl()

class PushInstruction(val value: VMValue) : VMInstructionImpl()

//-1, +1
class UnaryInstruction(val unaryExpr: LuaUnaryExpr) : VMInstructionImpl()

//-2, +1
class BinaryInstruction(val binaryExpr: LuaBinaryExpr) : VMInstructionImpl()