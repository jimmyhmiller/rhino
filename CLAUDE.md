@AGENTS.md

## TOP PRIORITY: Async Iteration Implementation

**THIS IS THE #1 PRIORITY. Implement async iteration support in Rhino.**

Async iteration (ES2018) enables asynchronous data streams with `async function*` generators and `for-await-of` loops. This is the last major async feature missing from Rhino.

### Current Status: Not Implemented

~1900 test262 tests are blocked by `{unsupported: [async-iteration]}`. This is a high-impact feature.

```bash
# Check async-iteration test count
grep -c "async-iteration" tests/testsrc/test262.properties
```

---

## What's Already Working (Async/Await - DONE!)

| Feature | Status | Notes |
|---------|--------|-------|
| `async function` declarations | ✅ Works | Parser, interpreter, compiled |
| `async function` expressions | ✅ Works | |
| `async` arrow functions | ✅ Works | `async () => {}` |
| `async` methods in classes | ✅ Works | Including static and private |
| `async` methods in objects | ✅ Works | |
| `await` expressions | ✅ Works | Proper precedence |
| Promise wrapping | ✅ Works | Return values wrapped |
| Error → rejection | ✅ Works | Thrown errors become rejections |
| `await` reserved in modules | ✅ Works | |
| `await` reserved in async | ✅ Works | Cannot use as identifier |

---

## Async Iteration Components to Implement

### 1. Async Generator Functions (`async function*`)

```javascript
async function* asyncGen() {
  yield 1;
  yield await Promise.resolve(2);
  yield 3;
}
```

**Key differences from regular generators:**
- Returns an AsyncGenerator object (not Generator)
- `yield` can await promises before yielding
- `next()` returns `Promise<{value, done}>` instead of `{value, done}`
- Has `[Symbol.asyncIterator]` instead of `[Symbol.iterator]`

### 2. `for-await-of` Loops

```javascript
for await (const item of asyncIterable) {
  console.log(item);
}
```

**Semantics:**
- Calls `[Symbol.asyncIterator]()` on the iterable
- Each iteration awaits the promise from `next()`
- Works with both async iterables AND sync iterables (wraps in promises)

### 3. Async Iterator Protocol

Objects implementing async iteration need:
- `[Symbol.asyncIterator]()` method returning an async iterator
- Async iterator has `next()` returning `Promise<{value, done}>`
- Optional `return()` and `throw()` methods (also return promises)

---

## Implementation Strategy

### Phase 1: Parser Support
1. Parse `async function*` declarations and expressions
2. Parse `for-await-of` loops
3. Track async generator context (both `yield` AND `await` valid)

### Phase 2: AST/IR
1. New `AsyncGeneratorFunction` type or flag
2. `ForAwaitOf` loop node
3. IR transformation for async generator state machine

### Phase 3: Runtime
1. `AsyncGeneratorFunction` constructor (`built-ins/AsyncGeneratorFunction`)
2. `AsyncGeneratorPrototype` with `next`, `return`, `throw`
3. `AsyncIteratorPrototype` (base for async iterators)
4. `AsyncFromSyncIteratorPrototype` (wraps sync iterators)

### Phase 4: Code Generation
1. Interpreter support for async generators
2. Compiled mode support

---

## Key Files to Modify

**Parser:**
- `Parser.java` - Parse `async function*` and `for-await-of`
- `TokenStream.java` - May need token updates

**AST:**
- `ast/FunctionNode.java` - Add async generator flag
- Possibly new `ForAwaitOf` node

**Runtime:**
- New `NativeAsyncGenerator.java`
- New `NativeAsyncGeneratorFunction.java`
- `ScriptRuntime.java` - Async iteration helpers

**Code Generation:**
- `IRFactory.java` - Transform async generators
- `CodeGenerator.java` - Interpreter bytecode
- `Codegen.java` / `BodyCodegen.java` - Compiled mode

---

## Test262 Tests

```bash
# Run async generator tests
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="language/statements/async-generator/*" -Dtest262raw --rerun-tasks

# Run for-await-of tests
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="language/statements/for-await-of/*" -Dtest262raw --rerun-tasks

# Run AsyncGenerator built-in tests
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="built-ins/AsyncGeneratorFunction/*" -Dtest262raw --rerun-tasks
```

---

## Spec References

- [AsyncGenerator Functions](https://tc39.es/ecma262/#sec-asyncgenerator-objects)
- [for-await-of](https://tc39.es/ecma262/#sec-for-in-and-for-of-statements)
- [Async Iteration Protocol](https://tc39.es/ecma262/#sec-asynciterable-interface)
- [AsyncFromSyncIterator](https://tc39.es/ecma262/#sec-async-from-sync-iterator-objects)

---

## Existing Foundation

Rhino has these building blocks ready:

| Component | Location | Notes |
|-----------|----------|-------|
| Generators | `NativeGenerator.java` | State machine model to follow |
| Promises | `NativePromise.java` | For async wrapping |
| Async functions | Throughout | Async context tracking |
| Symbol.asyncIterator | `NativeSymbol.java` | Well-known symbol exists |
| for-of loops | `Parser.java`, runtime | Sync iteration works |

---

## Fixing test262 tests

### Understanding test262.properties format

The `test262.properties` file lists **expected failures**. The test runner verifies behavior matches expectations:
- Tests listed in the file are expected to fail - if they fail, the test suite passes
- Tests NOT listed are expected to pass - if they pass, the test suite passes
- If a listed test starts passing (you fixed it!), the suite fails until you regenerate the file

Format: `category failures/total (failure%)`
- `built-ins/Error 3/53 (5.66%)` means 3 expected failures out of 53 tests
- Indented lines below a category are the specific **expected-to-fail** tests
- Categories prefixed with `~` are completely skipped
- Tests marked with `{unsupported: [...]}` require features Rhino doesn't support

### Running specific test262 tests

Use the `-Dtest262filter` system property with glob patterns to filter which tests run:

```bash
# Run all tests in a specific category
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest -Dtest262filter="built-ins/Array/*"

# Run a specific test file
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest -Dtest262filter="built-ins/Array/prototype/map/*"

# Run tests matching a pattern
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest -Dtest262filter="**/prop-desc.js"
```

### Seeing actual test results (raw mode)

By default, tests listed in test262.properties are expected to fail - if they fail, the test suite passes. To see actual pass/fail results, use `-Dtest262raw`:

```bash
# Run a test and see if it actually passes or fails
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest -Dtest262filter="built-ins/Array/prototype/map/*" -Dtest262raw

# Combine with --rerun-tasks to ensure fresh results
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest -Dtest262filter="built-ins/Error/*" -Dtest262raw --rerun-tasks
```

### Regenerating the properties file

When fixing test262 tests, regenerate the expected failures list:

```
RHINO_TEST_JAVA_VERSION=11 ./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest --rerun-tasks -DupdateTest262properties
```

**IMPORTANT**: After regenerating, you MUST verify that the changes to test262.properties are removing failures (tests that now pass), NOT adding new failures. Check the git diff carefully.
