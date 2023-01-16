## [Unreleased]

- **Requires IntelliJ IDEA 211 (2022.1)**
- **Support for declaring optional function parameters.** Supported in both short-form and long-form:
  - `fun(optionalParam?: number): void`
  - `---@param optionalParam? number`
- Improved displayed name for types, in particular anonymous tables. You'll now see something like `{ a: 1 }`, rather than just `table`.
- [Generic functions are now assignable to other generic functions](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/84).
- [General performance improvements, particularly when checking variance compatibility of deeply nested shapes](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/commit/b8c20ac2e42600ce25071d1c4237c4f6d4f0e49c).
- [Ensure global classes (i.e. built-ins) can be extended](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/commit/5db79fb622ea0f41185a688310d2567976699946).
- ["Multiple statements in one line" wrapping setting. Implemented by Jochem Pouwels](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/pull/49).
- [Improved completion by including literal named fields](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/commit/76350fda3cb49dc5f429406fc1b65e48eb868d31).
- [Improved support for lambda classes with static methods](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/commit/bd912ee8f2c3e2f24c311bc915395853c72f34e2).
- [Fixed incorrect overload matching when arguments were provided as multiple results from a function call](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/129).
- [Fixed incorrect type deductions in the presence of aliased generics](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/99).
- [Fixed unused local/param warnings incorrectly being reported in `.def.lua` files](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/129).
- [Fixed generic parameter resolution through aliases and shapes](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/99).
- [Fixed type of anonymous table members derived from generic params](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/commit/fe113c132100ca631b81b444ce014474f7e61387).
- [Fixed aliased shape equality](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/commit/30c757d993aeb8ab850905741daf259cffd18e86).
- [Fixed generic concreteness handling within function bodies (including recursive calls)](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/commit/1c46e798cda2b7f7743825292609a60d7224cf2d).
- [Fixed issue with tuples params passed to a callable type causing a stack overflow](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/87).
- [Fixed "}" sometimes being generated instead of "end". Implemented by Jochem Pouwels](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/pull/54).
- [Fixed incorrect declaration type inference in the presence of `@not`](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/commit/1a78ef5e35086bd67dd4f1ba95f15a3e0568b547).
- [Fixed generic resolution within type annotations on return statements](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/commit/eb3631a34632b3e8465d22a6837f756858aaad28).
- [Fixed debug.traceback() type definitions. Implemented by Sebastian Gładki](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/pull/106).

## [1.3.0]

- **Requires IntelliJ IDEA 211 (2021.1)**
- **Breaking:** The standard library `DebugInfo` class (returned from `debug.getinfo`) is now known as
  `std__DebugInfo` and is a shape rather than a class. Fields presence and types have also been corrected.
- New "Illegal alias in emmy doc" inspection. Reports illegal/circular alias definitions.
- [Fixed incorrect coercion of vararg types to unit types](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/82).
- [Fixed generic alias equality](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/85).
- [Fixed Lua 5.1-5.4 os.exit type definition](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/78).
- [Fixed Lua <= 5.1 os.exit documentation](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/79).
- [Fixed string:gmatch type definition](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/86).
- [Fixed file:read type definition](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/81).
- Fixed some issues with types displaying incorrectly in tooltips (missing parentheses).
- [Improved docs for string functions involving patterns in order to explain why captures are typed `string | number`](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/80).
- _You can now [sponsor](https://github.com/sponsors/Benjamin-Dobell) the ongoing development of Luanalysis and
  appear in the project's [README on Github](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis#sponsors). **Github
  are 1-to-1 matching any donations** up to $5000 (total) made before February 2022._

## [1.2.3]

- @class/@shape can now inherit from aliased types.
- [Made doc tables behave more like shapes.](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/67)
  As a result also improved doc table tuple support. Main outstanding differences:
    - Doc tables cannot inherit from shapes.
    - Doc tables cannot be callable types.
- Added support for doc strings trailing @alias definitions, displayed in IDE mouse-over popup.
- Added support for multiple @alias appearing in the same doc comment i.e. blank lines are no longer required between @alias.
- Improved indexing performance.
- Improved inference performance (IDE responsiveness), particularly in the presense of deeply nested scopes.
- Improved illegal inheritance inspection.
- Improved handling of table fields whose index type is unable to be inferred during indexing.
- Corrected/refined os.date() stdlib definition.
- [Worked around JBR/Kotlin initializer deadlock.](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/42).
- [Fixed generic inference when param is returned in multiple results.](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/68)
- [Fixed incorrect "Illegal override" of fun with multiple results.](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/65)
- [Fixed @vararg not accepting complex types (fun etc.)](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/61)
- Fixed occasional incorrect "Generic parameters cannot be shadowed".
- Fixed variance detection of fields being assigned to a generic shape where the index of the source field is covariant of the
  index of the target field, and the target field's type contains a generic parameter.
- Fixed auto-completion of aliases and unions.
- Fixed incorrect type-checking of arguments passed to `fun` (without a parameter list).
- Fixed inference of closures containing nested closures (containing return statements).
- Fixed lexing of aliases with constrained generics.

## [1.2.2]

- New "Illegal Overrides" inspection. Reports illegal member overrides when declaring a field/method on an inherited type.&lt;br />If you're overriding a method with an incompatible type (e.g. subclass `init` function with different parameters) then you should annotate the parent `init` (and child) as `@private`.
- Improved support for progressively adding type annotations to an existing Lua codebase. Essentially, local variables (without a declaration site assignment) and function parameters are now assumed to be of type `any`.
- Mouse-over documentation now supports indexed fields e.g. docs are now displayed when hovering over the `[1]` in `arr[1]`.
- Inspections now traverse expressions (function calls, binary operators etc.) so you'll see more specific errors within table literals in more circumstances.
- Many stdlib improvements/fixes (setmetatable, load* functions, getfenv, tostring, pcall, xpcall, assert & collectgarbage).
- Migrated stdlib to the .def.lua extension. If you're writing API types (i.e. files that are never executed) it's suggested you adopt this file extension too.
- [Formalised type widening behaviour for overridden fields](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/commit/230b7fbe51999c984e991c30ed09001a4b6f2297).
- Best effort type inference for the return type of invalid function calls i.e. more chance of just seeing errors at the call site, rather than all through-out a function.
- Special case handling for resolution of the `self` type when writing classes as closures i.e. expressions that look like `setmetatable(someTable, {__call=function() end})`. In such cases `self` type will be resolved based on the return type of `__call`.
- Various type inference and variance detection performance improvements.
- Improved handling of return types that are a union of multiple results. Rather than being flattened into a multiple result list where each component is a union during type checking, the initial structure is preserved.
- Ensured method override auto-completion works in more circumstances.
- Ensured primitives are never covariant of shapes.
- Made an attempt to ensure the plugin is considered ["dynamic"](https://jetbrains.org/intellij/sdk/docs/basics/plugin_structure/dynamic_plugins.html) by IDEA.
- Fixed clashes between anonymous types declared at the same offset in files with the same name (in different directories or virtual file systems).
- Fixed several complex type rendering issues (missing brackets etc.) in the UI.
- Fixed a boolean operator type inference bug in the presence of variadic return types.
- Fixed some incorrect inspections when "Unknown type (any) is indexable" is enabled".
- Fixed type inference/inspections involving assignment of `nil` to a member of `table&lt;K, nil | V&gt;`
- Fixed "Compute constant value" intention. However, many of the cases supported seem excessive and quite impractical. As such, this intention may be simplified in a future release.
- Fixed type inference & inspections for shapes that recursively reference themselves in their fields.
- Fixed auto-complete and type resolution for fields that are written to `self` typed variables.
- Fixed several bugs where generic parameters declared in multiple scopes are involved in type inference.
- Fixed support for generic parameters referring to other generic parameters in their type constraints.
- Fixed occasional misreported generic parameter shadowed errors.

## [1.2.1]

- Improved return inspection handling for unions of multiple results
- Improved stdlib string module definitions

## [1.2.0]

- **Requires IntelliJ IDEA 203 (2020.3)**
- **Breaking:** Removed unsafe assumed constructor functionality ([#12](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/12))
- **Lua 5.4 support:** &lt;const&gt; and &lt;close&gt; local variables and corresponding immutability inspections.
- Removed 'Recognize global name as type' plugin option as its use encourages unsafe development practices.
- Added new setting "Unknown type (any) is callable" which is enabled by default. For stricter type safety it's suggested you disable this option.
- Added an "Illegal inheritance" inspection which will report inheritance cycles and inheritance from primitives.
- Substantially improved (more refined) problem annotations on deeply nested table literals ([#11](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/11) and more.)
- Ensured inspections are re-run in more situations when variable types or type definitions are modified.
- Improved lookup performance for locally scoped types, currently just generic types.
- Performance improvements when dealing with types defined in the non-working file.
- Jump to definition behavior for fields that are members of a table, table&lt;K, V&gt; or V[].
- Added proper descriptions for every Luanalysis inspection.
- Improved mouse-over pop-up docs for table literals that will be interpreted as a shape.
- Only a subset of inspections are now run against files with the extension **.def.lua** e.g. return statement inspections are not run against functions
- Improved inspections for missing arguments and colon/period function calls e.g. "Missing self argument. Did you mean to call the method with a colon?"
- Corrected stdlib definitions for string.find()
- Corrected stdlib definitions for next, ipairs and pairs.
- Improved type safety of stdlib io module methods.
- Improved stdlib math.random() definition. Contributed by [Omniraptor](https://github.com/omniraptorr)
- Improved stdlib string.gsub() definition. Contributed by [Omniraptor](https://github.com/omniraptorr)
- Fixed several related bugs that could cause Luanalysis to get into a state where it was unable to recognise both user and in-built types.
- Fixed issue where shape inspections were appearing on table literals subject to a type cast ([#14](https://github.com/Benjamin-Dobell/IntelliJ-Luanalysis/issues/14))
- Static analysis now correctly handles use of parentheses to restrict multiple return values to just the first value.
- Fixed handling of iterators.
- Improved formatting/consistency of complex types displayed in the UI.
- _function_ type is now treated as returning _any..._
- Fixed use of _@overload_ in _@class_ definitions that do not proceed a variable declaration/assignment.
- Smarter union behavior that eliminates duplicate/covariant types from the union. This tends to result in much simple error message.
- Fixed handling of some situations where table literals ought to be interpreted as arrays, but were not.

 ## [1.1.0]
- Ctrl/Cmd + Click ("Go To") on a string literal argument of a require("file") now takes you to the return value of the referenced file, rather than simply opening the file.
- Fixed type resolution of require("file") expressions, where the referenced file has no return statements.
- Added/fixed support for negative number literal types.
- Type inference will now handle unary minus (negate) expressions, where the operand is a number literal. String literals representing values that Lua will _silently coerce_ to a number are also handled.

## [1.0.3]
- Ensured "Return type 'void' specified but no return values found" annotation is no longer raised for functions annotated as ---@return void.

## [1.0.2]
- Same as 1.0.1, but not constrained to IntelliJ 201 (2020.1).

## [1.0.1]
- Replaced duplicate class inspection with duplicate type inspection. Class/alias name collisions are now reported.
- Despite the Lua manual indicating otherwise, bit32 is present in Lua 5.3, added it.
- Ensured arrays are not covariant of shapes representing tuples.

## [1.0.0]
- Derived from EmmyLua 1.3.3
- A heap of static analysis improvements over EmmyLua 1.3.3
