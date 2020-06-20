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

---@alias StringTuple {[1]: IdentifierString, [2]: string}
---@alias NumberTuple {[1]: IdentifierNumber, [2]: number}

---@alias IdentifiedTuple StringTuple | NumberTuple

---@type IdentifiedTuple
local identifiedTuple

identifiedTuple = {STRING, "a string"}
identifiedTuple = {<error descr="Type mismatch. Required: 'IdentifierString' Found: 'IdentifierNumber'">NUMBER</error>, <error descr="Type mismatch. Required: 'number' Found: '\"a string\"'">"a string"</error>}

identifiedTuple = {<error descr="Type mismatch. Required: 'IdentifierNumber' Found: 'IdentifierString'">STRING</error>, <error descr="Type mismatch. Required: 'string' Found: '1'">1</error>}
identifiedTuple = {NUMBER, 1}
