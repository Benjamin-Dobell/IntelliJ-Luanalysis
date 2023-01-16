---@type number
local aNumber

---@type string
local aString

---@shape OurGenericShape<N>
---@field parameterOrNumber N | number
---@field aKnownStringLiteral 'a' | 'b' | 'c'

---@alias AliasAsParam 'one' | 'two' | 'three'

---@alias GenericAlias<N> string | OurGenericShape<N>

---@type GenericAlias<AliasAsParam>
local genericAlias = {
    parameterOrNumber = 'one',
    aKnownStringLiteral = 'a'
}

genericAlias = {
    parameterOrNumber = 'two',
    aKnownStringLiteral = 'b'
}

genericAlias = {
    parameterOrNumber = 1,
    aKnownStringLiteral = 'c'
}

genericAlias = {
    parameterOrNumber = <error descr="Type mismatch. Required: 'AliasAsParam | number' Found: '\"invalid\"'">'invalid'</error>,
    aKnownStringLiteral = 'a'
}

genericAlias = {
    parameterOrNumber = 'three',
    aKnownStringLiteral = <error descr="Type mismatch. Required: '\"a\" | \"b\" | \"c\"' Found: '\"invalid\"'">'invalid'</error>,
}

genericAlias = <error descr="Type mismatch. Missing member: 'aKnownStringLiteral' of: 'OurGenericShape<AliasAsParam>', on union candidate OurGenericShape<AliasAsParam>"><error descr="Type mismatch. Required: 'string' Found: '{ parameterOrNumber: \"owner\" }', on union candidate string">{
    parameterOrNumber = <error descr="Type mismatch. Required: 'AliasAsParam | number' Found: '\"owner\"', on union candidate OurGenericShape<AliasAsParam>">'owner'</error>
}</error></error>

genericAlias = 'a string'
genericAlias = <error descr="Type mismatch. Required: 'GenericAlias<AliasAsParam>' Found: '1'">1</error>
genericAlias = <error descr="Type mismatch. Missing member: 'aKnownStringLiteral' of: 'OurGenericShape<AliasAsParam>', on union candidate OurGenericShape<AliasAsParam>"><error descr="Type mismatch. Missing member: 'parameterOrNumber' of: 'OurGenericShape<AliasAsParam>', on union candidate OurGenericShape<AliasAsParam>"><error descr="Type mismatch. Required: 'string' Found: '{}', on union candidate string">{}</error></error></error>

---@type GenericAlias<"different">
local aDifferentGenericAlias = <error>genericAlias</error>

-- Defining multiple types in the one block is far from recommended, but we want our doc handling to be forgiving where possible.
---@alias AliasInSharedComment<T> string
---@alias AliasInSharedComment2<T> AliasInSharedComment<T>

---@alias AliasGenericIndexerDictionary<K, V> { [K]: V }

---@type AliasGenericIndexerDictionary<string, number>
local dictionaryStringToNumber

---@type AliasGenericIndexerDictionary<number, string>
local dictionaryNumberToString

aNumber = dictionaryStringToNumber[aString]
aNumber = <error descr="No such indexer '[number]' found on type '{ [K]: V }'">dictionaryStringToNumber[aNumber]</error>
aString = <error descr="Type mismatch. Required: 'string' Found: 'number'">dictionaryStringToNumber[aString]</error>
aString = <error descr="No such indexer '[number]' found on type '{ [K]: V }'">dictionaryStringToNumber[aNumber]</error>

aNumber = <error descr="No such indexer '[string]' found on type '{ [K]: V }'">dictionaryNumberToString[aString]</error>
aNumber = <error descr="Type mismatch. Required: 'number' Found: 'string'">dictionaryNumberToString[aNumber]</error>
aString = <error descr="No such indexer '[string]' found on type '{ [K]: V }'">dictionaryNumberToString[aString]</error>
aString = dictionaryNumberToString[aNumber]

dictionaryNumberToString[aNumber] = <error descr="Type mismatch. Required: 'string' Found: 'number'">dictionaryStringToNumber[aString]</error>
