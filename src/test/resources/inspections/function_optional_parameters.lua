---@type string
local aString

---@type number
local aNumber

---@type nil | string
local nilOrAString

---@param a? string
---@param b? string
---@param c? string
local function optionalParams1(a, b, c) end

optionalParams1()
optionalParams1(aString)
optionalParams1(aString, aString)
optionalParams1(aString, aString, aString)

optionalParams1(aString)
optionalParams1(aString, <error descr="Type mismatch. Required: 'string' Found: 'nil | string'">nilOrAString</error>)
optionalParams1(<error descr="Type mismatch. Required: 'string' Found: 'nil | string'">nilOrAString</error>, aString, aString)
optionalParams1(aString, <error descr="Type mismatch. Required: 'string' Found: 'nil | string'">nilOrAString</error>, aString)
optionalParams1(aString, aString, <error descr="Type mismatch. Required: 'string' Found: 'nil | string'">nilOrAString</error>)

---@type fun(a?: string, b?: string, c?: string)
local optionalParams1B

optionalParams1B()
optionalParams1B(aString)
optionalParams1B(aString, aString)
optionalParams1B(aString, aString, aString)

optionalParams1B(aString)
optionalParams1B(aString, <error descr="Type mismatch. Required: 'string' Found: 'nil | string'">nilOrAString</error>)
optionalParams1B(<error descr="Type mismatch. Required: 'string' Found: 'nil | string'">nilOrAString</error>, aString, aString)
optionalParams1B(aString, <error descr="Type mismatch. Required: 'string' Found: 'nil | string'">nilOrAString</error>, aString)
optionalParams1B(aString, aString, <error descr="Type mismatch. Required: 'string' Found: 'nil | string'">nilOrAString</error>)

---@param a? nil | string
---@param b? nil | string
---@param c? nil | string
local function optionalParams2(a, b, c) end

optionalParams2()
optionalParams2(aString)
optionalParams2(aString, aString)
optionalParams2(aString, aString, aString)

optionalParams2()
optionalParams2(nilOrAString)
optionalParams2(nilOrAString, nilOrAString)
optionalParams2(nilOrAString, nilOrAString, nilOrAString)

---@type fun(a?: nil | string, b?: nil | string, c?: nil | string)
local optionalParams2B

optionalParams2B()
optionalParams2B(aString)
optionalParams2B(aString, aString)
optionalParams2B(aString, aString, aString)

optionalParams2B()
optionalParams2B(nilOrAString)
optionalParams2B(nilOrAString, nilOrAString)
optionalParams2B(nilOrAString, nilOrAString, nilOrAString)

---@param a string
---@param b? string
---@param c? nil | string
local function optionalNilWithinScope(a, b, c)
    a = <error descr="Type mismatch. Required: 'string' Found: 'nil | string'">b</error>
    a = <error descr="Type mismatch. Required: 'string' Found: 'nil | string'">c</error>
    b = a
    b = c
    c = a
    c = b
end

---@type fun(a: string, b?: string, c?: nil | string)
local optionalNilWithinScopeB = function(a, b, c)
    a = <error descr="Type mismatch. Required: 'string' Found: 'nil | string'">b</error>
    a = <error descr="Type mismatch. Required: 'string' Found: 'nil | string'">c</error>
    b = a
    b = c
    c = a
    c = b
end

---@type fun(withOptionalParams: fun(a: string, b?: nil | string, c?: string))
local inferredOptionalNilWithinScope

inferredOptionalNilWithinScope(function(a, b, c)
    a = <error descr="Type mismatch. Required: 'string' Found: 'nil | string'">b</error>
    a = <error descr="Type mismatch. Required: 'string' Found: 'nil | string'">c</error>
    b = a
    b = c
    c = a
    c = b
end)

---@param a? string
local function inferredReturnWithOptional(a)
    return a
end

aString = <error descr="Type mismatch. Required: 'string' Found: 'nil | string'">inferredReturnWithOptional()</error>
nilOrAString = inferredReturnWithOptional()

---@param a? string
---@return string
local function recursiveOptionalParamForbiddenCheck(a)
    return recursiveOptionalParamForbiddenCheck(<error descr="Type mismatch. Required: 'string' Found: 'nil | string'">a</error>)
end

---@param a? string
---@vararg number
local function optionalParamWithVargs(a, ...) end

optionalParamWithVargs()
optionalParamWithVargs(aString)
optionalParamWithVargs(<error descr="Type mismatch. Required: 'string' Found: 'number'">aNumber</error>)
optionalParamWithVargs(aString, <error descr="Type mismatch. Required: 'number' Found: 'string'">aString</error>)
optionalParamWithVargs(aString, aNumber)
optionalParamWithVargs(aString, aNumber, aNumber)

---@type fun(a?: string, ...: number)
local optionalParamWithVargsB

optionalParamWithVargsB()
optionalParamWithVargsB(aString)
optionalParamWithVargsB(<error descr="Type mismatch. Required: 'string' Found: 'number'">aNumber</error>)
optionalParamWithVargsB(aString, <error descr="Type mismatch. Required: 'number' Found: 'string'">aString</error>)
optionalParamWithVargsB(aString, aNumber)
optionalParamWithVargsB(aString, aNumber, aNumber)

-- Variance

---@type fun(a: string, b?: string, c?: nil | string)
local funWithOptionals

---@type fun(a: string, b: string, c: nil | string)
local funWithoutOptionals

---@type fun(a: string, b: string, c: nil | string, d?: number)
local funWithAdditionalOptional

funWithOptionals = <error descr="Type mismatch. Required: 'fun(a: string, b?: string, c?: nil | string)' Found: 'fun(a: string, b: string, c: nil | string)'">funWithoutOptionals</error>
funWithoutOptionals = funWithOptionals

-- NOTE: Technically the combination of the following two variance rules is unsafe. However, generally the behavior is more useful than it is dangerous and
--       TypeScript has the same behavior, so we'll roll with it.
funWithoutOptionals = funWithAdditionalOptional
funWithAdditionalOptional = funWithoutOptionals

---@class ParentAddOptionalParamInheritanceClass
---@field method fun(): void

-- As above, the following is useful behavior, if not 100% safe.
---@class ChildAddOptionalParamInheritanceClass
---@field method fun(optional?: string): void

-- Declaration inspections

---@param a string
---@param b? string
---@param c string
local function requiredParamAfterOptionalForbidden(a, b, <error descr="Required parameters cannot follow optional parameters">c</error>) end

---@param a string
---@param c string
---@param b? string
local function requiredParamAfterOptionalForbidden2(a, b, <error descr="Required parameters cannot follow optional parameters">c</error>) end
