---@class ClassWithOverloads
local ClassWithOverloads = {}

--- Returns true if `tab` only contains sequential positive integer keys.
---@overload fun(tab: any[]): true
---@overload fun(tab: table): false
---@param tab table
---@return boolean
function ClassWithOverloads.isArray(tab)
    local i = 0
    for _ in pairs(tab) do
        i = i + 1
        if tab[i] == nil then return false end
    end
    return true
end

--- Returns true if `tab` only contains sequential positive integer keys.
---@overload fun(tab: any[]): true
---@overload fun(tab: table): false
---@param tab table
---@return boolean
ClassWithOverloads.isArrayFromClosure = function(tab)
    local i = 0
    for _ in pairs(tab) do
        i = i + 1
        if tab[i] == nil then return false end
    end
    return true
end

--- Returns true if `tab` only contains sequential positive integer keys.
---@overload fun(tab: any[]): true
---@overload fun(tab: table): false
---@param tab table
---@return boolean
local function isArray(tab)
    local i = 0
    for _ in pairs(tab) do
        i = i + 1
        if tab[i] == nil then return false end
    end
    return true
end

--- Returns true if `tab` only contains sequential positive integer keys.
---@overload fun(tab: any[]): true
---@overload fun(tab: table): false
---@param tab table
---@return boolean
local isArrayFromClosure = function(tab)
    local i = 0
    for _ in pairs(tab) do
        i = i + 1
        if tab[i] == nil then return false end
    end
    return true
end

---@type true
local t

---@type false
local f

t = ClassWithOverloads.isArray({1, 2, 3})
t = ClassWithOverloads.isArrayFromClosure({1, 2, 3})
t = isArray({1, 2, 3})
t = isArrayFromClosure({1, 2, 3})

f = <error descr="Type mismatch. Required: 'false' Found: 'true'">ClassWithOverloads.isArray({1, 2, 3})</error>
f = <error descr="Type mismatch. Required: 'false' Found: 'true'">ClassWithOverloads.isArrayFromClosure({1, 2, 3})</error>
f = <error descr="Type mismatch. Required: 'false' Found: 'true'">isArray({1, 2, 3})</error>
f = <error descr="Type mismatch. Required: 'false' Found: 'true'">isArrayFromClosure({1, 2, 3})</error>

f = ClassWithOverloads.isArray({one = 1, two = 2, three = 3})
f = ClassWithOverloads.isArrayFromClosure({one = 1, two = 2, three = 3})
f = isArray({one = 1, two = 2, three = 3})
f = isArrayFromClosure({one = 1, two = 2, three = 3})

t = <error descr="Type mismatch. Required: 'true' Found: 'false'">ClassWithOverloads.isArray({one = 1, two = 2, three = 3})</error>
t = <error descr="Type mismatch. Required: 'true' Found: 'false'">ClassWithOverloads.isArrayFromClosure({one = 1, two = 2, three = 3})</error>
t = <error descr="Type mismatch. Required: 'true' Found: 'false'">isArray({one = 1, two = 2, three = 3})</error>
t = <error descr="Type mismatch. Required: 'true' Found: 'false'">isArrayFromClosure({one = 1, two = 2, three = 3})</error>

---@param num number
---@return number...
---@overload fun(num: 4): number, number, number, number
---@overload fun(num: 3): number, number, number
---@overload fun(num: 2): number, number
---@overload fun(num: 1): number
function someStaticallyKnownReturnCounts(num)
    ---@type number[]
    local results = {}

    for i = 1, num do
        table.insert(results, num)
    end

    return table.unpack(results)
end

---@param a number
---@param b number
---@param c number
---@return string | number | boolean
---@overload fun(a: number): boolean
---@overload fun(a: number, b: number): number
---@overload fun(a: number, b: number, c: number): string
function overloadedWithDifferentReturnTypes(a, b, c)
    if c ~= nil then
        return ""
    elseif b ~= nil then
        return 1
    else
        return true
    end
end

---@param a number
---@param b number
---@param c number
---@return string | number | boolean
---@overload fun(a: number, b: number, c: number): string
---@overload fun(a: number, b: number): number
---@overload fun(a: number): boolean
function overloadedWithDifferentReturnTypes2(a, b, c)
    if c ~= nil then
        return ""
    elseif b ~= nil then
        return 1
    else
        return true
    end
end

---@type string
local aString

---@type number
local aNumber

---@type boolean
local aBoolean

aString = <error descr="Type mismatch. Required: 'string' Found: 'boolean'">overloadedWithDifferentReturnTypes(someStaticallyKnownReturnCounts(1))</error>
aNumber = <error descr="Type mismatch. Required: 'number' Found: 'boolean'">overloadedWithDifferentReturnTypes(someStaticallyKnownReturnCounts(1))</error>
aBoolean = overloadedWithDifferentReturnTypes(someStaticallyKnownReturnCounts(1))

aString = <error descr="Type mismatch. Required: 'string' Found: 'number'">overloadedWithDifferentReturnTypes(someStaticallyKnownReturnCounts(2))</error>
aNumber = overloadedWithDifferentReturnTypes(someStaticallyKnownReturnCounts(2))
aBoolean = <error descr="Type mismatch. Required: 'boolean' Found: 'number'">overloadedWithDifferentReturnTypes(someStaticallyKnownReturnCounts(2))</error>

aString = overloadedWithDifferentReturnTypes(someStaticallyKnownReturnCounts(3))
aNumber = <error descr="Type mismatch. Required: 'number' Found: 'string'">overloadedWithDifferentReturnTypes(someStaticallyKnownReturnCounts(3))</error>
aBoolean = <error descr="Type mismatch. Required: 'boolean' Found: 'string'">overloadedWithDifferentReturnTypes(someStaticallyKnownReturnCounts(3))</error>

aString = overloadedWithDifferentReturnTypes(someStaticallyKnownReturnCounts(4))
aNumber = <error descr="Type mismatch. Required: 'number' Found: 'string'">overloadedWithDifferentReturnTypes(someStaticallyKnownReturnCounts(4))</error>
aBoolean = <error descr="Type mismatch. Required: 'boolean' Found: 'string'">overloadedWithDifferentReturnTypes(someStaticallyKnownReturnCounts(4))</error>

aString = <error descr="Type mismatch. Required: 'string' Found: 'boolean'">overloadedWithDifferentReturnTypes2(someStaticallyKnownReturnCounts(1))</error>
aNumber = <error descr="Type mismatch. Required: 'number' Found: 'boolean'">overloadedWithDifferentReturnTypes2(someStaticallyKnownReturnCounts(1))</error>
aBoolean = overloadedWithDifferentReturnTypes2(someStaticallyKnownReturnCounts(1))

aString = <error descr="Type mismatch. Required: 'string' Found: 'number'">overloadedWithDifferentReturnTypes2(someStaticallyKnownReturnCounts(2))</error>
aNumber = overloadedWithDifferentReturnTypes2(someStaticallyKnownReturnCounts(2))
aBoolean = <error descr="Type mismatch. Required: 'boolean' Found: 'number'">overloadedWithDifferentReturnTypes2(someStaticallyKnownReturnCounts(2))</error>

aString = overloadedWithDifferentReturnTypes2(someStaticallyKnownReturnCounts(3))
aNumber = <error descr="Type mismatch. Required: 'number' Found: 'string'">overloadedWithDifferentReturnTypes2(someStaticallyKnownReturnCounts(3))</error>
aBoolean = <error descr="Type mismatch. Required: 'boolean' Found: 'string'">overloadedWithDifferentReturnTypes2(someStaticallyKnownReturnCounts(3))</error>

aString = overloadedWithDifferentReturnTypes2(someStaticallyKnownReturnCounts(4))
aNumber = <error descr="Type mismatch. Required: 'number' Found: 'string'">overloadedWithDifferentReturnTypes2(someStaticallyKnownReturnCounts(4))</error>
aBoolean = <error descr="Type mismatch. Required: 'boolean' Found: 'string'">overloadedWithDifferentReturnTypes2(someStaticallyKnownReturnCounts(4))</error>
