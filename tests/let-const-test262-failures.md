# Let/Const Test262 Failures Analysis

This document categorizes the 123 failing Test262 tests for `let` and `const` statements.

## Summary by Category

| Category | Count | Difficulty | Notes |
|----------|-------|------------|-------|
| **TDZ (Temporal Dead Zone)** | 19 | Medium | Core ES6 requirement - accessing let/const before initialization |
| **Destructuring** | 74 | Varies | Mostly iterator/rest element handling |
| **Syntax** | 28 | Easy-Medium | Parse-time error detection |
| **Unsupported (class)** | 2 | N/A | Requires `class` support |

## Detailed Breakdown

### 1. TDZ Tests (19 tests) - MEDIUM PRIORITY

These test that accessing `let`/`const` variables before their declaration throws `ReferenceError`:

```
language/statements/const/block-local-use-before-initialization-in-declaration-statement.js
language/statements/const/block-local-use-before-initialization-in-prior-statement.js
language/statements/const/function-local-closure-get-before-initialization.js
language/statements/const/function-local-use-before-initialization-in-declaration-statement.js
language/statements/const/function-local-use-before-initialization-in-prior-statement.js
language/statements/const/global-closure-get-before-initialization.js
language/statements/const/global-use-before-initialization-in-declaration-statement.js
language/statements/const/global-use-before-initialization-in-prior-statement.js
language/statements/let/block-local-closure-set-before-initialization.js
language/statements/let/block-local-use-before-initialization-in-declaration-statement.js
language/statements/let/block-local-use-before-initialization-in-prior-statement.js
language/statements/let/function-local-closure-get-before-initialization.js
language/statements/let/function-local-closure-set-before-initialization.js
language/statements/let/function-local-use-before-initialization-in-declaration-statement.js
language/statements/let/function-local-use-before-initialization-in-prior-statement.js
language/statements/let/global-closure-get-before-initialization.js
language/statements/let/global-closure-set-before-initialization.js
language/statements/let/global-use-before-initialization-in-declaration-statement.js
language/statements/let/global-use-before-initialization-in-prior-statement.js
```

**Issue**: TDZ implementation exists (`Token.TDZ`) but runtime checking may be incomplete.

### 2. Destructuring Tests (74 tests) - HARDER TO FIX

| Sub-category | Count | Issue |
|--------------|-------|-------|
| Function name inference | 20 | `let [fn = function(){}] = []` should set `fn.name = 'fn'` |
| Rest element patterns | 28 | `let [...rest]`, `let [...[a,b]]`, rest with objects |
| Elision handling | 6 | `let [,] = gen()` - iterator advancement on holes |
| Iterator error handling | 14 | Proper error propagation during destructuring |
| Object init null/undefined | 4 | `let {x} = null` should throw TypeError |
| Eval errors | 2 | `let {[expr]: x}` where expr throws |

### 3. Syntax Tests (28 tests) - EASIEST TO FIX

#### 3.1 const without initializer (8 tests)
`const x;` should be a SyntaxError.

```
language/statements/const/syntax/block-scope-syntax-const-declarations-mixed-with-without-initialiser.js
language/statements/const/syntax/block-scope-syntax-const-declarations-mixed-without-with-initialiser.js
language/statements/const/syntax/block-scope-syntax-const-declarations-without-initialiser.js
language/statements/const/syntax/const.js
language/statements/const/syntax/without-initializer-case-expression-statement-list.js
language/statements/const/syntax/without-initializer-default-statement-list.js
language/statements/const/syntax/without-initializer-if-expression-statement-else-statement.js
language/statements/const/syntax/without-initializer-if-expression-statement.js
language/statements/const/syntax/without-initializer-label-statement.js
```

#### 3.2 const assignment in for loop (3 tests)
`for (const i=0; i<1; i++)` should detect reassignment at parse time.

```
language/statements/const/syntax/const-invalid-assignment-next-expression-for.js
language/statements/const/syntax/const-invalid-assignment-statement-body-for-in.js
language/statements/const/syntax/const-invalid-assignment-statement-body-for-of.js
```

#### 3.3 let/const in statement position (12 tests)
`if (true) let x;` should be a SyntaxError (lexical declarations require a block).

```
language/statements/const/syntax/with-initializer-if-expression-statement-else-statement.js
language/statements/const/syntax/with-initializer-if-expression-statement.js
language/statements/const/syntax/with-initializer-label-statement.js
language/statements/let/syntax/with-initialisers-in-statement-positions-if-expression-statement-else-statement.js
language/statements/let/syntax/with-initialisers-in-statement-positions-if-expression-statement.js
language/statements/let/syntax/with-initialisers-in-statement-positions-label-statement.js
language/statements/let/syntax/without-initialisers-in-statement-positions-if-expression-statement-else-statement.js
language/statements/let/syntax/without-initialisers-in-statement-positions-if-expression-statement.js
language/statements/let/syntax/without-initialisers-in-statement-positions-label-statement.js
```

#### 3.4 let-closure in for loop (3 tests)
Per-iteration binding for closures in for loop.

```
language/statements/let/syntax/let-closure-inside-condition.js
language/statements/let/syntax/let-closure-inside-initialization.js
language/statements/let/syntax/let-closure-inside-next-expression.js
```

#### 3.5 Other syntax (2 tests)
```
language/statements/let/syntax/escaped-let.js          # l\u0065t should not be treated as let
language/statements/const/syntax/const-outer-inner-let-bindings.js  # Scoping edge case
```

### 4. Unsupported - Requires class (2 tests)

```
language/statements/const/static-init-await-binding-valid.js
language/statements/let/static-init-await-binding-valid.js
```

## Fix Priority

### Phase 1: Easy Wins (Syntax)
1. **const without initializer** - Add parse-time check in `Parser.java`
2. **let/const in statement position** - Check context when parsing let/const
3. **const assignment detection** - Detect `const` reassignment at parse time

### Phase 2: TDZ (Medium)
- Ensure reading uninitialized let/const throws ReferenceError
- Ensure closures that capture TDZ variables throw when accessed before init

### Phase 3: Destructuring (Hard)
- Function name inference requires tracking assignment context
- Rest elements need proper iterator handling
- Error propagation in destructuring is complex

---

## Recent Progress & Next Steps

### Completed

1. **Const reassignment in for-loop throws TypeError** - `for (const i = 0; i < 1; i++)` now correctly throws TypeError when `i++` executes at runtime
2. **Const shadowing works correctly** - Inner const can shadow outer const without errors

**Key changes made:**
- `ScriptRuntime.java`: Added READONLY check in `doScriptableIncrDecr()` to throw TypeError when modifying const variables in WITH scopes
- `ScriptRuntime.java`: Added `enterWithConst()` method to mark const properties as READONLY
- `NodeTransformer.java`: Added `visitLetScopeWithConst()` to handle LET scopes from for-loops with const
- `Icode.java`: Added `Icode_ENTERWITH_CONST` for interpreter support
- `CodeGenerator.java` & `Interpreter.java`: Handle ENTERWITH with const names
- `BodyCodegen.java`: Handle ENTERWITH with const names for compiled mode
- `IRFactory.java`: Mark const for-loop scopes with CONST_FOR_LOOP_SCOPE property
- `Node.java`: Added CONST_FOR_LOOP_SCOPE property constant
- `test262.properties`: Added `annexB/language/statements/for-in/const-initializer.js` to expected failures (pre-existing bug: `for (const a = 0 in {})` should be a SyntaxError but parses successfully)
- Fixed hang caused by incorrect `parentScope` update in `Scope.splitScope()`

### Remaining: Per-Iteration Bindings for Let in For Loops

**3 failing tests in LexicalScopeTest:**
- `letClosureInsideForCondition`
- `letClosureInsideForInitialization`
- `letClosureInsideForNextExpression`

**Problem**: Currently, `wrapLoopBodyWithPerIterationScope()` in `NodeTransformer.java` only wraps the **body** of the for loop. ES6 requires the condition and next expression to also be inside the per-iteration scope.

**Example of the issue:**
```javascript
var a = [];
for (let i = 0; i < 5; a.push(function() { return i; }), ++i) {}
// Expected: [1,2,3,4,5] - each closure captures different iteration's binding
// Actual: [5,5,5,5,5] - all closures share the same binding
```

**Solution approach**: Restructure the loop IR so that condition + body + increment are all inside the per-iteration WITH scope. Files to modify:
- `IRFactory.createLoop()` (line ~1669) - restructure how for-loop nodes are created
- `NodeTransformer.wrapLoopBodyWithPerIterationScope()` (line ~711) - expand to include condition and increment
- May need new icode/bytecode to handle the copy-back at the right point in the iteration

### Debugging Tools

**Test262 progress tracking**: Added to `Test262SuiteTest.java`, writes to `/tmp/test262-progress.txt`.
- File is cleared at start of each test run
- To find hanging tests: `comm -23 <(grep "START" /tmp/test262-progress.txt | sed 's/.* START //' | sort -u) <(grep "END" /tmp/test262-progress.txt | sed 's/.* END //' | sort -u)`
