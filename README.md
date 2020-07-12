# Luanalysis (an EmmyLua fork) for IntelliJ IDEA

An IDE for statically typed Lua development.

_Derived from [EmmyLua](https://emmylua.github.io/)._

![snapshot](/snapshot/overview.gif)

## Find usages
![find_usages](/snapshot/find_usages.gif)

## Rename
![rename](/snapshot/rename.gif)

## Parameter hints
![param_hints](/snapshot/param_hints.png)
![param_hints_cfg](/snapshot/param_hints_cfg.png)

## Go to symbol
![go_to_symbol](/snapshot/go_to_symbol.gif)

## Go to class
![go_to_class](/snapshot/go_to_class.gif)

## Quick Documentation(Ctrl + Q)
![quick_documentation](/snapshot/quick_documentation.gif)

## Method separators
![method_separators_cfg](/snapshot/method_separators_cfg.png)
![method_separators](/snapshot/method_separators.png)

## Method override line marker
![method_override_line_marker](/snapshot/method_override_line_marker.gif)

## Installation

Please download the latest build from the [releases page](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/releases).

To install the `.zip` you'll need to go to IntelliJ's...

    Preferences -> Plugins -> Settings Cog Icon -> Install Plugin from Disk...

![](https://user-images.githubusercontent.com/482276/82045348-4c06fd80-96f2-11ea-9b6d-e0e94fe2c67c.png)

Select the `.zip`, then when prompted restart IntelliJ.

## Features

Luanalysis is derived from EmmyLua and supports all the basic editing and refactoring functionality provided by [EmmyLua](https://github.com/EmmyLua/IntelliJ-EmmyLua).

Beyond basic Lua editing capabilities, Luanalysis supports a significant amount of additional functionality necessary to statically type advanced codebases.

_**Note**: Features are roughly listed in the order they were implemented, by no means order of importance._

#### Demo Project

A simple way to see what's possible in terms of static typing is probably to checkout the [demo project](https://github.com/Benjamin-Dobell/LuanalysisTypesDemo/).

### EmmyDoc Block Comments

![](https://user-images.githubusercontent.com/482276/77877267-022c9a00-72a1-11ea-9401-0ef6411d154e.png)

### Type casts

The `@type` tag can now we used to perform a cast when applied to a Lua expression. This is most useful with the newly added support for EmmyDoc block comments as we can easily specify inline type casts:

![](https://user-images.githubusercontent.com/482276/77877411-6d766c00-72a1-11ea-924a-27d58cee926f.png)

https://github.com/Benjamin-Dobell/LuanalysisTypesDemo/blob/master/src/type_casts.lua

### Improved variance detection

Inspections were previously attempting to determine if a type was assignable to another type simply by checking if the former is a "subtype" of latter, however variance wasn't formerly specified. However, this only works in the simple case, take for example functions where variance is well defined for function parameters:

![](https://user-images.githubusercontent.com/482276/77882315-7f114100-72ac-11ea-87c1-85eb022dd3d0.png)

EmmyLua does _not_ report the above error.

Additionally, union variance detection has been fixed:

![](https://user-images.githubusercontent.com/482276/77882669-2d1ceb00-72ad-11ea-87d5-139ea7b8a5f0.png)

As above, the current release of EmmyLua does not catch this error.

### Primitive literal types

![](https://user-images.githubusercontent.com/482276/77878045-196c8700-72a3-11ea-9302-d72a933ff3a4.png)

https://github.com/Benjamin-Dobell/LuanalysisTypesDemo/blob/master/src/string_literals.lua

### Referencing of generic parameter types inside functions

![](https://user-images.githubusercontent.com/482276/77878171-6e100200-72a3-11ea-8092-fecfcbe299ad.png)

https://github.com/Benjamin-Dobell/LuanalysisTypesDemo/blob/master/src/function_generics_scope.lua

### Binding of EmmyDoc to lambdas in assignments

i.e. Type checking now works inside function "lambdas" assigned to a variable with an EmmyDoc definition.

![](https://user-images.githubusercontent.com/482276/77878367-dd85f180-72a3-11ea-8084-899a471cb97c.png)

https://github.com/Benjamin-Dobell/LuanalysisTypesDemo/blob/master/src/lambda_params.lua

### Table type checking improvements

Various improvements, for example EmmyDoc "arrays" are now assignable to compatible table types e.g.

![](https://user-images.githubusercontent.com/482276/77882844-96046300-72ad-11ea-9446-89f526db5ac3.png)

The current EmmyLua release will report an error here even though this is sound.

https://github.com/Benjamin-Dobell/LuanalysisTypesDemo/blob/master/src/tables.lua

### Generic classes (!!!)

![](https://user-images.githubusercontent.com/482276/77882920-ccda7900-72ad-11ea-9d06-50603f8496da.png)

https://github.com/Benjamin-Dobell/LuanalysisTypesDemo/blob/master/src/generic_class_fields.lua

### Support for generic params referencing generic params

![](https://user-images.githubusercontent.com/482276/77883100-1e830380-72ae-11ea-8d16-b0a9ce4d26d7.png)

https://github.com/Benjamin-Dobell/LuanalysisTypesDemo/blob/master/src/function_generics.lua#L226-L249

### Generic inference fixes

The current EmmyLua release is unable to infer generics correctly in several situations and thus reports type errors where no error exists, and also misses errors where errors should exist e.g.

https://github.com/Benjamin-Dobell/LuanalysisTypesDemo/blob/master/src/function_generics.lua#L151-L178

### Type errors are now errors

By default, type safety errors are now reported as errors instead of warnings. This is made feasible by three things:

1. Many improvements in the ability to specify complex types
2. Type safety bug fixes
3. _Casting_

Casting in particular means that if a user is doing something the type system deems unsafe, but they know at runtime will be fine, they can just add a cast to signify this and the error will go away. 

### Generic parameter use _within a class_

![](https://user-images.githubusercontent.com/482276/77886778-2db97f80-72b5-11ea-8a31-b4dae1d05f5b.png)

Shadowing of a generic parameter is forbidden and an error reports:

![](https://user-images.githubusercontent.com/482276/77886846-4fb30200-72b5-11ea-9208-5c1082e5b967.png)

https://github.com/Benjamin-Dobell/LuanalysisTypesDemo/blob/master/src/generic_class_scope.lua

### New "No such member" inspection

![](https://user-images.githubusercontent.com/482276/77887071-ac162180-72b5-11ea-95f6-f46d0a5d330e.png)

https://github.com/Benjamin-Dobell/LuanalysisTypesDemo/blob/master/src/generic_class_fields.lua#L44-L45

### `self` is now a real type

Improved type checking for `self`, for example `self` can be assigned to a variable that matches the parent type of a method. However, that parent type cannot be assigned to `self`, as the class may be sub-classed (in which case `self` refers to a more specific type).

![](https://user-images.githubusercontent.com/482276/77887405-3d859380-72b6-11ea-888a-820a8eb6dea9.png)

https://github.com/Benjamin-Dobell/LuanalysisTypesDemo/blob/master/src/self.lua

### Assign type inspection now applies to variables assigned at variable declarations

![](https://user-images.githubusercontent.com/482276/77887588-92c1a500-72b6-11ea-9466-4e7e6a689afc.png)

Current EmmyLua release will allow this invalid assignment.

### Fixed a bug where the type inference cache was being used in the presence of an index

When a function returns multiple values, the current EmmyLua release will infer values and put them in the cache. This is inaccurate as generic types analysis may result in the same generic parameter being resolved differently based on the value being assigned, thus the cache cannot be used in this circumstance. Presently this results in both missing errors, and additional inaccurate errors, depending on the assignment.

### Added support for `@shape` (!!!)

A shape can be defined similarly to a class, except that contravariance is determined by compatibility of the members _not_ the inheritance hierarchy.

This is most useful when working with "structures" (e.g. JSON) rather than OOP classes.

![](https://user-images.githubusercontent.com/482276/77890009-7de71080-72ba-11ea-8d62-433585b893ce.png)

https://github.com/Benjamin-Dobell/LuanalysisTypesDemo/blob/master/src/shape.lua

What makes shapes particularly useful is that they support generics and inheritance (at definition time, not assignment) just like classes:

https://github.com/Benjamin-Dobell/LuanalysisTypesDemo/blob/master/src/shape.lua#L36-L74

Even _better_, type inspections are not just reported on incompatible `table`s as whole, but rather the inspections know how to traverse _table literals_ and provide detailed annotations of incompatibilities between two shapes e.g.

![](https://user-images.githubusercontent.com/482276/77890331-08c80b00-72bb-11ea-8540-f183ea1f7aee.png)

### Generic Aliases (!!!)

Aliases can now take generic parameters, just like a class or shape.

![](https://user-images.githubusercontent.com/482276/77890560-68beb180-72bb-11ea-9cb3-5698befbc6d1.png)

https://github.com/Benjamin-Dobell/LuanalysisTypesDemo/blob/master/src/generic_alias.lua

### Improved vararg syntax

Function types can now use `...: T` as an alternative to `vararg T`:

![](https://user-images.githubusercontent.com/482276/80113190-53d8f380-85c5-11ea-828e-6fa8ea76ddb6.png)

### Variadic return values (!!!)

We now support variadic return values:

![](https://user-images.githubusercontent.com/482276/80111259-052a5a00-85c3-11ea-9f77-a9775ec9769e.png)

Internally, `TyTuple` has been replaced with `TyMultipleResults` to reflect the fact that this construct is not fixed size. Additionally, multiple results are now properly handled in more locations.

### Standard library type improvements

Various improvements to typings of Lua built-ins taking advantage of variadic return values etc.

### Support for indexed fields (!!!)

We can now type all properties of tables, not just string constants. Given that this PR also adds support for primitive literal types we can use this a lot of different ways e.g.

![](https://user-images.githubusercontent.com/482276/80122378-d0250400-85d0-11ea-9592-a0e3d8a841ac.png)

Here we have regular string identifier fields, number literal fields `[1]`, `[2]` and `[3]` _and_ a `[boolean]` field. That last one is really powerful, because it's _not_ a constant, it's a real type.

We can type custom data structures e.g.

```lua
---@class Dictionary<K, V>
---@field [K] V
```

This will work correctly for any `K` and everything will be statically type checked as you'd expect.

There's also syntax for table types, it works for table literals _and_ anonymous classes (i.e. tables that aren't explicitly typed):

![](https://user-images.githubusercontent.com/482276/80122834-6c4f0b00-85d1-11ea-8940-f9e9220dea10.png)

### Partially typed functions

We now support `fun` types with optional parameter lists and optional return values i.e. `fun: boolean` and `fun(arg: boolean)`. `fun` (with neither specified) also works for posterity but is functionally equivalent to the existing `function` type.

Partially typed functions are extremely useful for implementing callback and handler patterns. For example, it's quite common to have an extensible event system where each event has unique arguments, but the handler must return `true` to indicate the event was handled:

![](https://user-images.githubusercontent.com/482276/80123609-77ef0180-85d2-11ea-9b24-e0ce5b1c2016.png)

### Callable types (!!!)

This is another _really_ useful feature. We can now properly indicate that an object is callable (i.e. is a `table` whose metatable has a `__call` method).

![](https://user-images.githubusercontent.com/482276/80123975-f0ee5900-85d2-11ea-95dc-fd13c161e180.png)

This is done by using the existing `@overload` EmmyDoc keyword, and works similarly i.e. we can specify many overloads and type checking and completion will work as you'd expect:

![](https://user-images.githubusercontent.com/482276/80124216-462a6a80-85d3-11ea-8bf5-960c57defaf7.png)

### 


## Building

  `./gradlew build_201`

For more details about the Jetbrains Platform SDK please refer to the [official documentation](https://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/setting_up_environment.html).

## Developed By

Luanalysis by:
[Benjamin Dobell](https://github.com/Benjamin-Dobell)

EmmyLua by:
[**@tangzx** 阿唐](https://github.com/tangzx)

**Contributors**

Please [refer to Github](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/graphs/contributors) for a complete list of contributors.
