---@class LambdaClass
---@overload fun(a: number): LambdaClass
local LambdaClass = {}

setmetatable(LambdaClass,  {
    ---@param a number
    __call = function(_, a)
        local self = --[[---@type LambdaClass]] {}

        ---@return number
        function self.getNumber()
            return a
        end

        return self
    end
})

local missingArg = LambdaClass(<error descr="Missing argument: a: number">)</error>
local wrongArg = LambdaClass(<error descr="Type mismatch. Required: 'number' Found: '\"one\"'">"one"</error>)
local lambdaClass = LambdaClass(1)

---@type number
local aNumber = lambdaClass.getNumber()

---@type string
local aString = <error descr="Type mismatch. Required: 'string' Found: 'number'">lambdaClass.getNumber()</error>


---@class ParentLambdaClassWithStatic

---@class ParentLambdaClassWithStatic__static
---@overload fun(): ParentLambdaClassWithStatic
local ParentLambdaClassWithStatic = {}

---@class LambdaClassWithStatic

---@class LambdaClassWithStatic__static
---@overload fun(): LambdaClassWithStatic
local LambdaClassWithStatic = {}

function LambdaClassWithStatic.staticMethod()
end

setmetatable(LambdaClassWithStatic, {
    ---@return LambdaClassWithStatic
    __call = function(_)
        local self = --[[---@type self]] ParentLambdaClassWithStatic()

        ---@return self
        function  self.method()
            return self
        end

        return self
    end,
})

---@type LambdaClassWithStatic
local lambdaClassWithStatic

---@type LambdaClassWithStatic__static
local lambdaClassWithStatic__static

---@type ParentLambdaClassWithStatic
local parentLambdaClassWithStatic

---@type ParentLambdaClassWithStatic__static
local parentLambdaClassWithStatic__static

lambdaClassWithStatic.method()
<error descr="Unknown function 'method'."><error descr="No such member 'method' found on type 'LambdaClassWithStatic__static'">lambdaClassWithStatic__static.method</error>()</error>
<error descr="Unknown function 'method'."><error descr="No such member 'method' found on type 'ParentLambdaClassWithStatic'">parentLambdaClassWithStatic.method</error>()</error>
<error descr="Unknown function 'method'."><error descr="No such member 'method' found on type 'ParentLambdaClassWithStatic__static'">parentLambdaClassWithStatic__static.method</error>()</error>

<error descr="Unknown function 'staticMethod'."><error descr="No such member 'staticMethod' found on type 'LambdaClassWithStatic'">lambdaClassWithStatic.staticMethod</error>()</error>
lambdaClassWithStatic__static.staticMethod()
<error descr="Unknown function 'staticMethod'."><error descr="No such member 'staticMethod' found on type 'ParentLambdaClassWithStatic'">parentLambdaClassWithStatic.staticMethod</error>()</error>
<error descr="Unknown function 'staticMethod'."><error descr="No such member 'staticMethod' found on type 'ParentLambdaClassWithStatic__static'">parentLambdaClassWithStatic__static.staticMethod</error>()</error>
