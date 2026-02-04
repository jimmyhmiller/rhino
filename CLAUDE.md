@AGENTS.md

## TOP PRIORITY: Async/Await Implementation

**THIS IS THE #1 PRIORITY. Implement full async/await support in Rhino.**

Async/await is ES2017's flagship feature and critical for modern JavaScript. Currently Rhino has no async function support - this is our next major milestone.

### Current Status: Not Implemented

```bash
# Check async-related test262 failures
node scripts/test-status.js 2017 --all | grep -i async
```

---

## Implementation Overview

Async/await builds on top of Promises and Generators. The basic transformation is:

```javascript
// User writes:
async function fetchData() {
  const response = await fetch(url);
  const data = await response.json();
  return data;
}

// Conceptually becomes something like:
function fetchData() {
  return new Promise((resolve, reject) => {
    // State machine based on generator-like suspension points
  });
}
```

### Key Components to Implement

1. **Lexer/Parser** (`TokenStream.java`, `Parser.java`)
   - Add `async` keyword recognition
   - Parse `async function` declarations and expressions
   - Parse `async` arrow functions: `async () => {}`
   - Parse `await` expressions inside async functions
   - Parse `async` methods in classes and object literals
   - Track async context for `await` validity

2. **AST Nodes** (`ast/` directory)
   - May need new AST node types or flags for async functions
   - `await` expression node

3. **IR Generation** (`IRFactory.java`)
   - Transform async functions into state machines
   - Similar approach to generators but returning Promises
   - Handle `await` as suspension points

4. **Runtime Support** (`ScriptRuntime.java`, new classes)
   - Async function execution context
   - Promise integration for await
   - Proper error propagation (rejected promises)

5. **Code Generation** (`CodeGenerator.java`, `Codegen.java`)
   - Both interpreter and compiled modes need async support

### Implementation Strategy

**Phase 1: Basic async/await**
- `async function` declarations
- `await` on Promises
- Return value automatically wrapped in Promise
- Thrown errors become rejected Promises

**Phase 2: Full syntax support**
- `async` arrow functions
- `async` methods in classes
- `async` methods in object literals
- `await` in default parameter expressions (edge case)

**Phase 3: Edge cases and spec compliance**
- `for-await-of` loops (async iteration)
- Top-level await (module context)
- Proper handling of non-Promise await operands
- AsyncGenerator functions (`async function*`)

### Key Spec References

- [ES2017 Async Functions](https://tc39.es/ecma262/#sec-async-function-definitions)
- [Await Expression](https://tc39.es/ecma262/#sec-await)
- [AsyncFunction Objects](https://tc39.es/ecma262/#sec-async-function-objects)

---

## Existing Foundation

Rhino already has these prerequisites working:

| Feature | Status | Notes |
|---------|--------|-------|
| Promises | ✅ Works | `NativePromise.java` - basic, all, race |
| Generators | ✅ Works | State machine infrastructure exists |
| Arrow functions | ✅ Works | |
| Classes | ✅ Works | For async methods |
| ES6 Modules | ✅ Works | For future top-level await |

The generator implementation in particular provides a model for how to implement the state machine transformation needed for async/await.

---

## Test262 Async Tests

Test262 has comprehensive async/await tests. Currently they're likely skipped or failing:

```bash
# Run async function tests
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="language/statements/async-function/*" -Dtest262raw --rerun-tasks

# Run async expression tests
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="language/expressions/async-function/*" -Dtest262raw --rerun-tasks

# Run await tests
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="language/expressions/await/*" -Dtest262raw --rerun-tasks

# Run async method tests
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="language/statements/class/async-*" -Dtest262raw --rerun-tasks
```

---

## ES6 Status (Previous Focus)

ES6 is at 91.4% passing (789 failures remaining). Key remaining gaps:

- Iterator close behavior (~25 for-of tests)
- Destructuring from iterables (Set/Map)
- Default parameter TDZ violations

These are lower priority than async/await but can be addressed opportunistically.

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

The filter supports `*` (matches any characters) and `?` (matches single character) wildcards.

### Seeing actual test results (raw mode)

By default, tests listed in test262.properties are expected to fail - if they fail, the test suite passes. To see actual pass/fail results, use `-Dtest262raw`:

```bash
# Run a test and see if it actually passes or fails
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest -Dtest262filter="built-ins/Array/prototype/map/*" -Dtest262raw

# Combine with --rerun-tasks to ensure fresh results
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest -Dtest262filter="built-ins/Error/*" -Dtest262raw --rerun-tasks
```

In raw mode, the test suite will fail if any test actually fails, regardless of whether it's listed in test262.properties.

### Workflow for fixing tests

1. Pick a failing test from test262.properties
2. Read the test file in `tests/test262/test/` to understand what it tests
3. Make your fix to the Rhino source code
4. Regenerate the properties file (see below)
5. Verify git diff shows the test removed from expected failures (not added!)

### Regenerating the properties file

When fixing test262 tests, regenerate the expected failures list:

```
RHINO_TEST_JAVA_VERSION=11 ./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest --rerun-tasks -DupdateTest262properties
```

The `RHINO_TEST_JAVA_VERSION=11` is required because test results can vary by Java version.

**IMPORTANT**: After regenerating, you MUST verify that the changes to test262.properties are removing failures (tests that now pass), NOT adding new failures. Check the git diff carefully. If there are new failures, you MUST confirm with me before proceeding - sometimes failures are expected but they need explicit approval.
