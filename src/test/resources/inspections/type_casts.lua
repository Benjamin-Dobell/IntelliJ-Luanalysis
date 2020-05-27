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
<error descr="Type mismatch. Required: 'string' Found: 'number'">aString</error>, <error descr="Type mismatch. Required: 'number' Found: 'string'">aNumber</error> = <error descr="Type mismatch. Required: 'number' Found: 'string'"><error descr="Type mismatch. Required: 'string' Found: 'number'">multiReturn()</error></error> -- Expect error
aString, aNumber = --[[---@type string, number]] multiReturn()
aString, <error descr="Type mismatch. Required: 'number' Found: 'string'">aNumber</error> = <error descr="Type mismatch. Required: 'number' Found: 'string'">--[[---@type string, string]] multiReturn()</error> -- Expect error
