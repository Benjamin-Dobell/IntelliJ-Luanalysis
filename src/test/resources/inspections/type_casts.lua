---@param returnString boolean
---@return string|number
local function stringOrNumber(returnString)
    return returnString and "someString" or 1
end

---@param n number
function wantsNumber(n)
end

wantsNumber(<error descr="Type mismatch. Required: 'number' Found: 'number|string'">stringOrNumber(false)</error>)
wantsNumber(--[[---@type number]] stringOrNumber(false))
wantsNumber(--[[---@not string]] stringOrNumber(false))
wantsNumber(<error descr="Type mismatch. Required: 'number' Found: 'string'">--[[---@not number]] stringOrNumber(false)</error>)

wantsNumber(
        ---@type number @Single line doc comments also work as type casts
        stringOrNumber(false)
)

wantsNumber(<error descr="Type mismatch. Required: 'number' Found: 'fun(): any'">--[[---@type fun(): any]] 1</error>)

---@param arr any[]
function wantsArray(arr)
end

local aString = "aString"

wantsArray(<error descr="Type mismatch. Required: 'any[]' Found: 'string'">aString</error>)
wantsArray(--[[--- @type any[] ]] aString) -- Trailing space used to separate array ']' from the block comment ']]'.


local aNumber = 1

---@return number, string
local function multiReturn()
return 1, "a string"
end

aNumber, aString = multiReturn()
<error descr="Type mismatch. Required: 'string' Found: 'number'">aString</error>, <error descr="Type mismatch. Required: 'number' Found: 'string'">aNumber</error> = <error descr="Result 1, type mismatch. Required: 'string' Found: 'number'"><error descr="Result 2, type mismatch. Required: 'number' Found: 'string'">multiReturn()</error></error>
aString, aNumber = --[[---@type string, number]] multiReturn()
aString, <error descr="Type mismatch. Required: 'number' Found: 'string'">aNumber</error> = <error descr="Result 2, type mismatch. Required: 'number' Found: 'string'">--[[---@type string, string]] multiReturn()</error>


---@type number|nil
local numberOrNil

wantsNumber(numberOrNil)
wantsNumber(--[[---@not nil]] numberOrNil)

---@param returnNumbers boolean
---@return number|string, number|string
local function multiReturn2(returnNumbers)
return stringOrNumber(returnNumbers), stringOrNumber(returnNumbers)
end


<error descr="Type mismatch. Required: 'number' Found: 'number|string'">aNumber</error>, <error descr="Type mismatch. Required: 'number' Found: 'number|string'">aNumber</error> = <error descr="Result 1, type mismatch. Required: 'number' Found: 'number|string'"><error descr="Result 2, type mismatch. Required: 'number' Found: 'number|string'">multiReturn2(true)</error></error>
aNumber, aNumber = --[[---@not string, string]] multiReturn2(true)
aNumber, aString = --[[---@not string, number]] multiReturn2(true)
aNumber = <error descr="Type mismatch. Required: 'number' Found: 'number|string'">multiReturn2(true)</error>
aNumber = --[[---@not string]] multiReturn2(true)
