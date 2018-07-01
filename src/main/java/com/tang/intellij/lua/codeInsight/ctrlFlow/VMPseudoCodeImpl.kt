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

open class VMPseudoCodeImpl : VMPseudoCode {

    private var labelIndex = 0

    override val parent: VMPseudoCode? = null
    override val instructions = mutableListOf<VMInstruction>()
    override val localDeclarationInstructions: List<VMLocalDeclarationInstruction>
        get() = emptyList()

    override fun addInstruction(inst: VMInstruction) {
        inst.index = instructions.size
        inst.owner = this
        instructions.add(inst)
    }

    override fun bindLabel(label: VMLabel) {
        label.offset = instructions.size
    }

    override fun createJump(): VMJump {
        return VMJump(VMLabel("L${labelIndex++}"), VMLabel("L${labelIndex++}"))
    }
}