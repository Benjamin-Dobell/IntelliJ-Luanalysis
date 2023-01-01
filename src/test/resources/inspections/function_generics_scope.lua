---@generic K
---@param a K
---@param b table<K, K>
---@return K
function globalFunction(a, b)
    ---@type K
    local genericTypedVar = a
    return genericTypedVar
end

---@generic K
---@param a K
---@param b table<K, K>
---@return K
local function localFunction(a, b)
    ---@type K
    local genericTypedVar = a
    return genericTypedVar
end

---@class Scope
local Scope = {}

---@generic K
---@param a K
---@param b table<K, K>
---@return K
function Scope.method(a, b)
    ---@type K
    local genericTypedVar = a
    return genericTypedVar
end

---@generic K
---@param a K
---@param b table<K, K>
---@return K
local assignedFunction = function(a, b)
    ---@type K
    local genericTypedVar = a
    return genericTypedVar
end

---@generic K
---@param a K
---@return K
local outerFunction = function(a)
    ---@param b K
    local innerFunction = function(b)
        ---@type K
        local v
    end

    innerFunction(<error descr="Type mismatch. Required: 'K' Found: '\"someValue\"'">"someValue"</error>)
    return a
end

outerFunction("someValue")

local tableWithGenericFunction = {
    ---@generic T
    ---@param t T
    genericFunction = function(t)
        ---@type T
        local alsoT = t
    end
}

---@generic T
---@param value T
---@return fun(): T
local function scopedGenericAnnotatedReturnStatement(value)
    ---@type fun(): T
    return function()
        return value
    end
end

---@generic V
---@param value V
---@return V
function recursiveConcreteness(value)
    ---@type V
    local v

    value = v
    v = value

    local resultantVFromRecursiveCall = recursiveConcreteness(v)

    v = resultantVFromRecursiveCall
    resultantVFromRecursiveCall = v

    ---@type boolean
    local differentVForRecursiveCall
    local resultantVFromDifferentVRecursiveCall = recursiveConcreteness(differentVForRecursiveCall)

    differentVForRecursiveCall = resultantVFromDifferentVRecursiveCall
    resultantVFromDifferentVRecursiveCall = differentVForRecursiveCall

    v = <error descr="Type mismatch. Required: 'V' Found: 'boolean'">differentVForRecursiveCall</error>
    v = <error descr="Type mismatch. Required: 'V' Found: 'boolean'">resultantVFromDifferentVRecursiveCall</error>

    differentVForRecursiveCall = <error descr="Type mismatch. Required: 'boolean' Found: 'V'">v</error>
    resultantVFromDifferentVRecursiveCall = <error descr="Type mismatch. Required: 'boolean' Found: 'V'">v</error>

    return value
end

---@generic T
---@param t T
function anonymousGenericFieldAccessedFromExternalScope(t)
    return {
        a = t
    }
end

---@type boolean
local aBoolean
local booleanFoo = anonymousGenericFieldAccessedFromExternalScope(aBoolean)

aBoolean = booleanFoo.a
booleanFoo.a = <error descr="Type mismatch. Required: 'boolean' Found: '\"not a boolean\"'">"not a boolean"</error>
