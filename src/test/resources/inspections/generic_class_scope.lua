---@class GenericInMethod<T>
---@field a T
local GenericInMethod = {}

---@param arg T
function GenericInMethod:colonMethod(arg)
    ---@type T
    local thing

    thing = self.a
end

---@param object self
---@param arg T
function GenericInMethod.dotMethod(object, arg)
    ---@type T
    local thing

    thing = object.a
end

---@param object self
---@param arg T
GenericInMethod.lambdaMethod = function(object, arg)
    ---@type T
    local thing

    thing = object.a
end

---@generic <error descr="Generic parameters cannot be shadowed, 'T' was previously defined on line 1">T</error>
---@param arg T
function GenericInMethod:colonMethodShadow(arg)
end

---@generic <error descr="Generic parameters cannot be shadowed, 'T' was previously defined on line 1">T</error>
---@param arg T
function GenericInMethod:dotMethodShadow(arg)
end

---@generic <error descr="Generic parameters cannot be shadowed, 'T' was previously defined on line 1">T</error>
---@param arg T
GenericInMethod.lambdaMethodShadow = function(arg)
end

---@class Concreteness<T>
---@field myT T
local Concreteness = {}

---@return T
function Concreteness:getT()
    ---@type T
    local t
    return t
end

---@param t T
function Concreteness:setT(t)
end

function Concreteness:testConcreteClassGenericWithinImplementationScope(t)
    ---@type T
    local t

    t = self:getT()
    self:setT(t)

    self.myT = self:getT()
    self:setT(self.myT)

    ---@type Concreteness<number>
    local numberConcreteness
    numberConcreteness:setT(<error descr="Type mismatch. Required: 'number' Found: 'T'">self:getT()</error>)
    self:setT(<error descr="Type mismatch. Required: 'T' Found: 'number'">numberConcreteness:getT()</error>)
    self.myT = <error descr="Type mismatch. Required: 'T' Found: 'number'">numberConcreteness:getT()</error>
    numberConcreteness:setT(<error descr="Type mismatch. Required: 'number' Found: 'T'">self.myT</error>)

    t = <error descr="Type mismatch. Required: 'T' Found: 'number'">numberConcreteness:getT()</error>
    numberConcreteness:setT(<error descr="Type mismatch. Required: 'number' Found: 'T'">t</error>)

    t = <error descr="Type mismatch. Required: 'T' Found: 'number'">numberConcreteness.myT</error>
    numberConcreteness.myT = <error descr="Type mismatch. Required: 'number' Found: 'T'">t</error>
end
