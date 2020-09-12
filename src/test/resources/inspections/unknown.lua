---@param num number
local function wantsNumber(num) end

---@type number
local aNumber

---@type string
local aString

---@type any
local unknown

local implicitUnknown = <warning descr="Undeclared variable 'notAVarA'.">notAVarA</warning>

wantsNumber(aNumber)
wantsNumber(<error descr="Type mismatch. Required: 'number' Found: 'string'">aString</error>)
wantsNumber(unknown)
wantsNumber(implicitUnknown)
wantsNumber(<warning descr="Undeclared variable 'notAVarA'.">notAVarA</warning>)

aNumber = aNumber
aNumber = <error descr="Type mismatch. Required: 'number' Found: 'string'">aString</error>
aNumber = unknown

aString = aString
aString = <error descr="Type mismatch. Required: 'string' Found: 'number'">aNumber</error>
aString = unknown

unknown = unknown
unknown = aNumber
unknown = aString
