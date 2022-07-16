local InferredTable = {}

local CONSTANTS = {
    A = 'a',
}

InferredTable.SOMETHING = {
    [CONSTANTS.A] = true,
}

InferredTable.SOMETHING.b = 1

local x = InferredTable.SOMETHING.<caret>
