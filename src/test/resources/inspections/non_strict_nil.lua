---@type nil | number
local nilOrANumber

---@type number
local aNumber

nilOrANumber = nil
aNumber = nil
aNumber = nilOrANumber

local buffer = {1, nil, 3}
local a, b, c = table.unpack(buffer)

aNumber = a
aNumber = b
aNumber = c
