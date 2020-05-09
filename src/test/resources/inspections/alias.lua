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
