@AGENTS.md

## TOP PRIORITY: Language Feature Completion

**GOAL: Get language/ test262 tests from 19,425 to 23,000+ passing (83% → 98%)**

We're focusing on completing JavaScript language feature support. The `language/` section of test262 covers core syntax and semantics - this is the heart of the JavaScript engine.

### Current Status

```
language/     19,425 / 23,409 passing (83.0%)
built-ins/    15,247 / 22,954 passing (66.4%)
─────────────────────────────────────────────
Total         35,617 / 49,149 passing (72.5%)
```

To hit 23,000 language tests, we need to fix ~3,575 more tests.

---

## Major Missing Language Features (by impact)

### 1. Dynamic Import - 626 tests (100% failing)
```javascript
// NOT WORKING
const module = await import('./module.js');
```
- `import()` expression not implemented
- Subcategories: syntax (192), usage (108), catch (176), namespace (67)

### 2. Private Field Compound/Logical Assignment - ~130 tests
```javascript
// NOT WORKING
class C {
  #x = 1;
  inc() { this.#x += 1; }      // Compound assignment fails
  set() { this.#x ||= 5; }     // Logical assignment fails
}
```
- Simple assignment works (`this.#x = 1`)
- Compound (`+=`, `-=`) and logical (`&&=`, `||=`, `??=`) don't work

### 3. Module Code Issues - 297 tests (51% failing)
- Top-level await (~200 tests)
- Import attributes
- Import defer

### 4. Class Remaining Issues - ~1,800 tests (21% failing)
- Static block edge cases (reject await/yield/return)
- Decorator syntax (11 tests, Stage 3)
- Invalid private name escapes (28 tests)

### 5. Eval Code Edge Cases - 184 tests (53% failing)
- `arguments` binding in various contexts

---

## Quick Wins (Recommended Order)

| Feature | Tests | Difficulty |
|---------|-------|------------|
| **Private field compound assignment** | ~100 | Medium - wire up existing operators |
| **Static block restrictions** | ~20 | Easy - add parser checks |
| **Private name escape sequences** | 28 | Easy - parser validation |

---

## What's Already Working

| Feature | Status |
|---------|--------|
| Classes (methods, fields, private, static) | ✅ 79% |
| Async/await | ✅ Works |
| Async generators (`async function*`) | ✅ Works |
| `for-await-of` loops | ✅ Works |
| Generators | ✅ Works |
| Destructuring | ✅ Works |
| Spread/rest | ✅ Works |
| Arrow functions | ✅ 95% |
| Template literals | ✅ Works |
| Optional chaining (`?.`) | ✅ Works |
| Nullish coalescing (`??`) | ✅ Works |
| Logical assignment (`&&=`, `||=`, `??=`) | ✅ Works (except private fields) |

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
