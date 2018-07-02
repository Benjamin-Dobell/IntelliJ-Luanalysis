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

import com.tang.intellij.lua.codeInsight.ctrlFlow.instructions.InstructionVisitor

class VMScope(val parent: VMScope?) {
    val depth: Int = (parent?.depth ?: 0) + 1

    val localDeclarationInstructions = mutableListOf<VMLocalDeclarationInstruction>()
}

data class VMLabel(val name: String, var offset: Int = 0)

data class VMJump(val start: VMLabel, val end: VMLabel)

interface VMInstruction {
    var index: Int

    var owner: VMPseudoCode

    var scope: VMScope

    fun accept(visitor: InstructionVisitor)
}

interface VMLocalDeclarationInstruction : VMInstruction {
    val name: String

    val value: VMValue
}

interface VMPseudoCode {
    val parent: VMPseudoCode?

    val instructions: List<VMInstruction>

    val localDeclarationInstructions: List<VMLocalDeclarationInstruction>

    fun addInstruction(inst: VMInstruction)

    fun bindLabel(label: VMLabel)

    fun createJump(): VMJump
}

interface VMState {
    fun push(value: VMValue)

    fun pop(): VMValue

    fun peek(): VMValue
}