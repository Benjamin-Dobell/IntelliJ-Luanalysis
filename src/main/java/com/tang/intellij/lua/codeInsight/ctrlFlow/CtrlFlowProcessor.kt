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

import com.tang.intellij.lua.codeInsight.ctrlFlow.instructions.BinaryInstruction
import com.tang.intellij.lua.codeInsight.ctrlFlow.instructions.PushInstruction
import com.tang.intellij.lua.codeInsight.ctrlFlow.instructions.UnaryInstruction
import com.tang.intellij.lua.psi.*

class CtrlFlowProcessor : LuaVisitor() {

    private val builder = CtrlFlowInstructionsBuilderImpl()

    private lateinit var factory: VMValueFactory

    fun process(block: LuaBlock) {
        block.accept(this)
    }

    override fun visitBlock(o: LuaBlock) {
        builder.enterScope(o)
        super.visitBlock(o)
        builder.exitScope(o)
    }

    override fun visitLocalDef(o: LuaLocalDef) {
        val list = o.exprList?.exprList
        var unsure = false
        o.nameList?.nameDefList?.forEachIndexed { index, def ->
            val varValue = factory.createVariableValue(def)
            builder.addInstruction(PushInstruction(varValue))

            val expr = list?.getOrNull(index)
            if (expr is LuaCallExpr) unsure = true
            when {
                unsure -> pushUnknown()
                expr == null -> pushNil()
                else -> expr.accept(this)
            }


        }
    }

    override fun visitAssignStat(o: LuaAssignStat) {
    }

    override fun visitTableExpr(o: LuaTableExpr) {
        //TODO
        pushUnknown()
    }

    override fun visitLiteralExpr(o: LuaLiteralExpr) {

    }

    override fun visitBinaryExpr(o: LuaBinaryExpr) {
        o.left?.accept(this) ?: pushUnknown()
        o.right?.accept(this) ?: pushUnknown()
        builder.addInstruction(BinaryInstruction(o))
    }

    override fun visitUnaryExpr(o: LuaUnaryExpr) {
        o.expr?.accept(this) ?: pushUnknown()
        builder.addInstruction(UnaryInstruction(o))
    }

    override fun visitCallExpr(o: LuaCallExpr) {
        //TODO
        pushUnknown()
    }

    override fun visitClassMethodDef(o: LuaClassMethodDef) {
    }

    private fun pushUnknown() {
        builder.addInstruction(PushInstruction(VMUnknown))
    }

    private fun pushNil() {

    }
}