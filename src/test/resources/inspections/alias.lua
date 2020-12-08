---@class ClassToBeAliased
---@field a string

---@alias MyAlias ClassToBeAliased

---@type MyAlias
local myAlias

---@type ClassToBeAliased
local classToBeAliased

classToBeAliased = myAlias
classToBeAliased = <error descr="Type mismatch. Required: 'ClassToBeAliased' Found: '1'">1</error>

myAlias = classToBeAliased
myAlias = <error descr="Type mismatch. Required: 'ClassToBeAliased' Found: '1'">1</error>

---@alias UnionAlias string|ClassToBeAliased

---@type string
local aString

---@type UnionAlias
local unionAlias

unionAlias = aString
aString = <error descr="Type mismatch. Required: 'string' Found: 'UnionAlias'">unionAlias</error>

unionAlias = classToBeAliased
classToBeAliased = <error descr="Type mismatch. Required: 'ClassToBeAliased' Found: 'UnionAlias'">unionAlias</error>

unionAlias = myAlias
myAlias = <error descr="Type mismatch. Required: 'ClassToBeAliased' Found: 'UnionAlias'">unionAlias</error>

---@alias AliasedFunction fun(a: string): void

---@type AliasedFunction
local aliasedFunction

aliasedFunction(<error descr="Type mismatch. Required: 'string' Found: '1'">1</error>)
aliasedFunction("okay")

---@type fun(a: MyAlias, b: MyAlias): void
local myAliasFun

myAliasFun(classToBeAliased, <error descr="Type mismatch. Required: 'ClassToBeAliased' Found: '1'">1</error>)
myAliasFun(classToBeAliased, classToBeAliased)


---@alias IdentifierString 0
---@alias IdentifierNumber 1

---@type IdentifierString
local STRING = 0

---@type IdentifierNumber
local NUMBER = 1

---@alias Identifier IdentifierString | IdentifierNumber

---@alias StringTupleAlias {[1]: IdentifierString, [2]: string}
---@alias NumberTupleAlias {[1]: IdentifierNumber, [2]: number}

---@alias IdentifiedTuple StringTupleAlias | NumberTupleAlias

---@type IdentifiedTuple
local identifiedTuple

identifiedTuple = {STRING, "a string"}
identifiedTuple = {<error descr="Type mismatch. Required: 'IdentifierString' Found: 'IdentifierNumber'">NUMBER</error>, <error descr="Type mismatch. Required: 'number' Found: '\"a string\"'">"a string"</error>}

identifiedTuple = {<error descr="Type mismatch. Required: 'IdentifierNumber' Found: 'IdentifierString'">STRING</error>, <error descr="Type mismatch. Required: 'string' Found: '1'">1</error>}
identifiedTuple = {NUMBER, 1}

---@type Identifier
local identifier = identifiedTuple[1]

STRING = <error descr="Type mismatch. Required: '0' Found: 'IdentifierNumber | IdentifierString'">identifiedTuple[1]</error>
NUMBER = <error descr="Type mismatch. Required: '1' Found: 'IdentifierNumber | IdentifierString'">identifiedTuple[1]</error>

identifiedTuple[1] = <error descr="Type mismatch. Required: '1' Found: 'IdentifierString' on union member { [1]: IdentifierNumber, [2]: number }">STRING</error>
identifiedTuple[1] = <error descr="Type mismatch. Required: '0' Found: 'IdentifierNumber' on union member { [1]: IdentifierString, [2]: string }">NUMBER</error>

---@alias NumberArray number[]
---@alias StringArray string[]

---@type NumberArray
local aliasedNumberArray

---@type number[]
local numberArray

---@type StringArray
local aliasedStringArray

aliasedNumberArray = numberArray
numberArray = aliasedNumberArray

aliasedNumberArray = {1, 2, 3}
numberArray = {1, 2, 3}

aliasedNumberArray = <error descr="Type mismatch. Required: 'number[]' Found: 'StringArray'">aliasedStringArray</error>
aliasedStringArray = <error descr="Type mismatch. Required: 'string[]' Found: 'NumberArray'">aliasedNumberArray</error>


---@generic K, V
---@param tab table<K, V>
local function acceptArrayAsGenericTable(tab)
end

---@alias SomeAliasedType number
---@alias SomeAliasedArrayOfAliasedType SomeAliasedType[]

---@type SomeAliasedArrayOfAliasedType
local aliasedArrayOfAliasedType

acceptArrayAsGenericTable({1, 2, 3})
acceptArrayAsGenericTable(aliasedArrayOfAliasedType)
acceptArrayAsGenericTable(<error descr="Type mismatch. Required: 'table<K, V>' Found: '1'">1</error>)
