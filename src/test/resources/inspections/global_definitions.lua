---@class GlobalClass
GlobalClass = {}

---@type number
GlobalClass.someNumber = 1

function GlobalClass.doSomething()
end

GlobalAnonymousClass = {}

---@type number
GlobalAnonymousClass.anotherNumber = 1

function GlobalAnonymousClass.doSomethingElse()
end

GlobalAnonymousClass.anotherNumber = 2

--- Extend built-in global without explicitly referring to its type

---@param a number
function math.extension(a)
    return a
end
