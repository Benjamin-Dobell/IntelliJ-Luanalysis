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

import com.tang.intellij.lua.codeInsight.inspection.AssignTypeInspection
import com.tang.intellij.lua.codeInsight.inspection.MatchFunctionSignatureInspection
import com.tang.intellij.lua.codeInsight.inspection.ReturnTypeInspection
import com.tang.intellij.lua.codeInsight.inspection.UndeclaredMemberInspection
import com.tang.intellij.lua.codeInsight.inspection.doc.GenericConstraintInspection
import com.tang.intellij.lua.codeInsight.inspection.doc.GenericParameterShadowed
import com.tang.intellij.lua.lang.LuaLanguageLevel
import com.tang.intellij.lua.project.LuaSettings

class TypeSafetyTest : LuaInspectionsTestBase(
        AssignTypeInspection(),
        GenericConstraintInspection(),
        GenericParameterShadowed(),
        MatchFunctionSignatureInspection(),
        ReturnTypeInspection(),
        UndeclaredMemberInspection()
) {
    fun check(filename: String, checkWarn: Boolean = true, checkInfo: Boolean = false, checkWeakWarn: Boolean = false) {
        LuaSettings.instance.isNilStrict = true
        checkByFile(filename, checkWarn, checkInfo, checkWeakWarn)
        LuaSettings.instance.isNilStrict = false
    }

    fun testAlias() {
        check("alias.lua")
    }

    fun testBoolean() {
        check("boolean.lua")
    }

    fun testClass() {
        check("class.lua")
    }

    fun testFunctionClosures() {
        check("function_closures.lua")
    }

    fun testFunctionCovariance() {
        check("function_covariance.lua")
    }

    fun testFunctionGenerics() {
        check("function_generics.lua")
    }

    fun testFunctionGenericsScope() {
        check("function_generics_scope.lua")
    }

    fun testFunctionMultipleReturns() {
        check("function_multiple_returns.lua", true, false, true)
    }

    fun testFunctionPartiallyTyped() {
        check("function_partially_typed.lua")
    }

    fun testGenericAlias() {
        // TODO: There's a bug in IntelliJ's XML descr attribute parsing. Once fixed we should add descr.
        check("generic_alias.lua")
    }

    fun testGenericClassConstraints() {
        check("generic_class_constraints.lua")
    }

    // TODO: Uncomment once https://youtrack.jetbrains.com/issue/IJSDK-799 is resolved.
    /*fun testGenericClassCovariance() {
        check("generic_class_covariance.lua")
    }*/

    fun testGenericClassFields() {
        check("generic_class_fields.lua")
    }

    fun testGenericClassScope() {
        check("generic_class_scope.lua")
    }

    // TODO: Uncomment once https://youtrack.jetbrains.com/issue/IJSDK-799 is resolved.
    /*fun testGenericSelf() {
        check("generic_self.lua")
    }*/

    fun testImplicitTypes() {
        check("implicit_types.lua")
    }

    fun testIndexedFields() {
        check("indexed_fields.lua")
    }

    fun testLambdaClass() {
        check("lambda_class.lua")
    }

    fun testLambdaParams() {
        check("lambda_params.lua")
    }

    fun testLocalDefAssignment() {
        check("local_def_assignment.lua")
    }

    fun testModules() {
        myFixture.configureByFiles("moduleA.lua", "moduleA_reference.lua")
        LuaSettings.instance.languageLevel = LuaLanguageLevel.LUA51
        LuaSettings.instance.isNilStrict = true
        enableInspection()
        myFixture.checkHighlighting(true, false, false)
        LuaSettings.instance.languageLevel = LuaLanguageLevel.LUA53
        LuaSettings.instance.isNilStrict = false
    }

    fun testNumbers() {
        check("numbers.lua")
    }

    fun testOps() {
        check("ops.lua")
    }

    fun testOverloads() {
        check("overloads.lua")
    }

    fun testRecursiveAlias() {
        check("recursive_alias.lua")
    }

    fun testSelf() {
        check("self.lua")
    }

    fun testShape() {
        check("shape.lua")
    }

    fun testSnippet() {
        check("snippet.lua")
    }

    fun testStrictNil() {
        check("strict_nil.lua")
    }

    fun testStringLiterals() {
        check("string_literals.lua")
    }

    fun testTables() {
        check("tables.lua")
    }

    fun testTrailingType() {
        check("trailing_type.lua")
    }

    fun testTypeCasts() {
        check("type_casts.lua")
    }

    fun testUnions() {
        // TODO: As above, there's a bug in IntelliJ's XML descr attribute parsing. Once fixed we should add descr.
        check("unions.lua")
    }

    fun testUnknown() {
        check("unknown.lua")
    }

    fun testVarargs() {
        check("varargs.lua")
    }

    fun testVarreturn() {
        check("varreturn.lua")
    }
}
