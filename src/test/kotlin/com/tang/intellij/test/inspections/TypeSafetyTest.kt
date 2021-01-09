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

import com.tang.intellij.lua.codeInsight.inspection.*
import com.tang.intellij.lua.codeInsight.inspection.doc.GenericConstraintInspection
import com.tang.intellij.lua.codeInsight.inspection.doc.GenericParameterShadowed
import com.tang.intellij.lua.lang.LuaLanguageLevel
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.project.StdLibraryProvider

class TypeSafetyTest : LuaInspectionsTestBase(
        AssignTypeInspection(),
        GenericConstraintInspection(),
        GenericParameterShadowed(),
        IllegalOverrideInspection(),
        MatchFunctionSignatureInspection(),
        ReturnTypeInspection(),
        UndeclaredMemberInspection(),
        UndeclaredVariableInspection()
) {
    fun check(filename: String, checkWarn: Boolean = true, checkInfo: Boolean = false, checkWeakWarn: Boolean = false) {
        LuaSettings.instance.isNilStrict = true
        LuaSettings.instance.isUnknownCallable = false
        LuaSettings.instance.isUnknownIndexable = false
        checkByFile(filename, checkWarn, checkInfo, checkWeakWarn)
        LuaSettings.instance.isNilStrict = false
        LuaSettings.instance.isUnknownCallable = true
        LuaSettings.instance.isUnknownIndexable = true
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
        // TODO: Add descr to <error> tags once https://youtrack.jetbrains.com/issue/IJSDK-799 is resolved
        check("generic_alias.lua")
    }

    fun testGenericClassConstraints() {
        check("generic_class_constraints.lua")
    }

    fun testGenericClassCovariance() {
        // TODO: Add descr to <error> tags once https://youtrack.jetbrains.com/issue/IJSDK-799 is resolved
        check("generic_class_covariance.lua")
    }

    fun testGenericClassFields() {
        check("generic_class_fields.lua")
    }

    fun testGenericClassScope() {
        check("generic_class_scope.lua")
    }

    fun testGenericSelf() {
        // TODO: Add descr to <error> tags once https://youtrack.jetbrains.com/issue/IJSDK-799 is resolved
        check("generic_self.lua", true)
    }

    fun testGlobalDefinitions() {
        check("global_definitions.lua")
    }

    fun testGlobalUsage() {
        myFixture.configureByFile("global_definitions.lua")
        check("global_usage.lua")
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
        val defaultLanguageLevel = LuaSettings.instance.languageLevel
        LuaSettings.instance.languageLevel = LuaLanguageLevel.LUA51
        StdLibraryProvider.reload()
        LuaSettings.instance.isNilStrict = true
        LuaSettings.instance.isUnknownCallable = false
        LuaSettings.instance.isUnknownIndexable = false

        myFixture.configureByFiles("moduleA.lua", "moduleA_reference.lua")
        enableInspection()
        myFixture.checkHighlighting(true, false, false)

        LuaSettings.instance.languageLevel = defaultLanguageLevel
        StdLibraryProvider.reload()
        LuaSettings.instance.isNilStrict = false
        LuaSettings.instance.isUnknownCallable = true
        LuaSettings.instance.isUnknownIndexable = true
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

    fun testOverrides() {
        check("overrides.lua")
    }

    fun testPoorlyNamedParams() {
        check("poorly_named_params.lua")
    }

    fun testRecursiveAlias() {
        check("recursive_alias.lua")
    }

    fun testRequire() {
        myFixture.configureByFile("requireB.lua")
        myFixture.configureByFile("requireC.lua")
        check("requireA.lua")
    }

    fun testSelf() {
        check("self.lua", true)
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
        // TODO: Add descr to <error> tags once https://youtrack.jetbrains.com/issue/IJSDK-799 is resolved
        check("unions.lua")
    }

    fun testUnknown() {
        LuaSettings.instance.isUnknownCallable = true
        LuaSettings.instance.isUnknownIndexable = true
        checkByFile("unknown.lua")
        LuaSettings.instance.isUnknownCallable = false
        LuaSettings.instance.isUnknownIndexable = false
    }

    fun testUnknownStrict() {
        check("unknown_strict.lua")
    }

    fun testVarargs() {
        check("varargs.lua")
    }

    fun testVarreturn() {
        check("varreturn.lua")
    }
}
