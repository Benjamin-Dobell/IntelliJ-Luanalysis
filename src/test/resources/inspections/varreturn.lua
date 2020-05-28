---@type number
local aNumber

---@type boolean
local aBoolean

---@type fun(): number, boolean...
local varreturnFunction

aNumber, aBoolean, aBoolean = varreturnFunction()
aNumber, aBoolean, aBoolean, <error descr="Type mismatch. Required: 'number' Found: 'boolean'">aNumber</error>, aBoolean = <error descr="Result 4, type mismatch. Required: 'number' Found: 'boolean'">varreturnFunction()</error>


---@param numberParam number
---@return number, boolean...
local function varreturnFunction2()
    if aNumber == 1 then
        return 1
    elseif aNumber == 2 then
        return 1, <error descr="Result 2, type mismatch. Required: 'boolean' Found: '\"not a boolean\"'">"not a boolean"</error>
    elseif aNumber == 3 then
        return 1, true
    elseif aNumber == 4 then
        return 1, true, false
    else
        <error descr="Type mismatch. Required: 'number' Found: 'nil'">return</error> -- Expect
    end
end

aNumber, aBoolean, aBoolean = varreturnFunction2()
aNumber, aBoolean, aBoolean, <error descr="Type mismatch. Required: 'number' Found: 'boolean'">aNumber</error>, aBoolean = <error descr="Result 4, type mismatch. Required: 'number' Found: 'boolean'">varreturnFunction2()</error>

---@param a number
---@param b string
local function acceptsNumberString(a, b) end

acceptsNumberString(<error descr="Result 2, type mismatch. Required: 'string' Found: 'boolean'">varreturnFunction2()</error><error descr="Missing argument: b: string">)</error>

---@param a number
---@vararg string
local function acceptsNumberVariadicString(a, ...) end

acceptsNumberVariadicString(<error descr="Variadic result, type mismatch. Required: 'string' Found: 'boolean'">varreturnFunction2()</error>)

---@type fun(): boolean...
local varreturnFunction3

aBoolean, aBoolean = varreturnFunction3()
aBoolean, aBoolean, <error descr="Type mismatch. Required: 'number' Found: 'boolean'">aNumber</error>, aBoolean = <error descr="Result 3, type mismatch. Required: 'number' Found: 'boolean'">varreturnFunction3()</error>

---@return boolean...
local function varreturnFunction4()
    if aNumber == 1 then
        return
    elseif aNumber == 2 then
        return <error descr="Type mismatch. Required: 'boolean' Found: '\"not a boolean\"'">"not a boolean"</error>
    elseif aNumber == 3 then
        return true
    elseif aNumber == 4 then
        return true, false
    end
end

aBoolean, aBoolean = varreturnFunction4()
aBoolean, aBoolean, <error descr="Type mismatch. Required: 'number' Found: 'boolean'">aNumber</error>, aBoolean = <error descr="Result 3, type mismatch. Required: 'number' Found: 'boolean'">varreturnFunction4()</error>

---@generic T
---@param list T[]
---@return T...
local function genericVarreturn(list)
    return table.unpack(list)
end

aNumber, aNumber = genericVarreturn({1, 2})
aNumber, <error descr="Type mismatch. Required: 'boolean' Found: '1|2'">aBoolean</error> = <error descr="Result 2, type mismatch. Required: 'boolean' Found: '1|2'">genericVarreturn({1, 2})</error>

local implicitNumber1, implicitNumber2 = genericVarreturn({1, 2})

aNumber = implicitNumber1
aBoolean = <error descr="Type mismatch. Required: 'boolean' Found: '1|2'">implicitNumber1</error>
aNumber = implicitNumber2
aBoolean = <error descr="Type mismatch. Required: 'boolean' Found: '1|2'">implicitNumber2</error>

---@return 1, 2, 3
local function returns123()
    return 1, 2, 3
end

---@type number[]
local numberArray = {returns123()}
