---@type nil | number
local nilOrANumber

---@type number
local aNumber

nilOrANumber = nil
aNumber = <error descr="Type mismatch. Required: 'number' Found: 'nil'">nil</error>
aNumber = <error descr="Type mismatch. Required: 'number' Found: 'nil | number'">nilOrANumber</error>

local buffer = {1, nil, 3}
local a, b, c = table.unpack(buffer)

aNumber = <error descr="Type mismatch. Required: 'number' Found: '1 | 3 | nil'">a</error>
aNumber = <error descr="Type mismatch. Required: 'number' Found: '1 | 3 | nil'">b</error>
aNumber = <error descr="Type mismatch. Required: 'number' Found: '1 | 3 | nil'">c</error>
