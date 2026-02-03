@AGENTS.md

## TOP PRIORITY: 100% ES6 Test262 Conformance

**THIS IS THE #1 PRIORITY. We will not stop until ALL ES6 tests pass.**

The goal is to achieve 100% passing rate for ES6 test262 tests. This is non-negotiable - keep fixing tests until there are zero failures.

### Current ES6 Status: 89.8% passing (939 failures remaining)

```bash
node scripts/test-status.js 6  # See current status
```

### ✅ Recently Fixed

- **ES6 Modules**: All 49/49 tests passing
- **Generator Method Destructuring**: Fixed 359 tests (destructuring now runs before generator creation)

---

### NEXT PRIORITY: for-of Statement Tests (139 failures)

**Total for-of failures: 139/619 (22.5%)**

These are the highest-value ES6 fixes remaining. Run: `node scripts/test-status.js 6 --all | grep for-of`

#### Breakdown by Issue Type

| Issue | Count | Description |
|-------|-------|-------------|
| **Iterator Close** | ~80 | `IteratorClose` not called properly |
| **Function Name Inference** | 5 | `obj-id-init-fn-name-*` tests |
| **Iterator Protocol** | 4 | `iterator-next-*` edge cases |
| **Computed Property Eval** | 2 | `obj-prop-name-evaluation*` |
| **Other Destructuring** | ~48 | Various initialization/assignment issues |

#### Priority 1: Iterator Close Behavior (~80 tests)

The biggest issue: Rhino doesn't properly call `IteratorClose` (the iterator's `return()` method) when:
- Array destructuring doesn't exhaust the iterator
- An error occurs during destructuring
- A `break`/`return` exits the loop early

**Example test** (`array-elem-iter-nrml-close.js`):
```javascript
var returnCount = 0;
var iterator = {
  next: function() { return { done: false }; },
  return: function() { returnCount += 1; return {}; }
};
iterable[Symbol.iterator] = () => iterator;

for ([ _ ] of [iterable]) {
  // After destructuring [_], iterator.return() should be called
  assert.sameValue(returnCount, 1);  // FAILS: Rhino doesn't call return()
}
```

**Test patterns:**
- `*-nrml-close*` - Normal completion, iterator not exhausted
- `*-rtrn-close*` - Return/break exits loop
- `*-thrw-close*` - Error thrown during iteration
- `*-close-err*` - Error in close itself
- `*-close-null*` - return() returns null
- `*-close-skip*` - Cases where close should NOT be called

**Key files to investigate:**
- `ScriptRuntime.java` - `toIterator()`, iteration helpers
- `NativeIterator.java` - Iterator protocol implementation
- `IRFactory.java` - for-of compilation

**Run iterator close tests:**
```bash
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="language/statements/for-of/dstr/*-close*.js" -Dtest262raw --rerun-tasks
```

#### Priority 2: Function Name Inference (5 tests)

When destructuring with default values, anonymous functions should get the property name:

```javascript
for ({ fn = function() {} } of [{}]) {
  assert.sameValue(fn.name, 'fn');  // FAILS: name not set
}
```

**Test files:** `obj-id-init-fn-name-*.js` (fn, gen, class, arrow, cover)

**Key files:** `ScriptRuntime.java` - `setFunctionName()` calls during destructuring

#### Priority 3: Iterator Protocol Edge Cases (4 tests)

```javascript
// iterator-next-result-type.js - next() must return object
// iterator-next-result-value-attr-error.js - error getting .value
// iterator-next-reference.js - next as reference
// iterator-next-error.js - next() throws
```

#### Priority 4: Body Errors with Iterator Close (2 tests)

```javascript
// body-dstr-assign-error.js - destructuring error should close iterator
// body-put-error.js - assignment error should close iterator
```

When destructuring in `for ([x.attr] of iterable)` throws, iterator must be closed.

#### Run All for-of Tests

```bash
# See all for-of failures
node scripts/test-status.js 6 --all | grep for-of

# Run all for-of tests
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="language/statements/for-of/*" -Dtest262raw --rerun-tasks

# Run just destructuring tests
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="language/statements/for-of/dstr/*" -Dtest262raw --rerun-tasks
```

---

### Other ES6 Gaps (lower priority after for-of)

| Category | Failures | Notes |
|----------|----------|-------|
| Class tests | ~45 | Mostly computed property eval, yield in cpn |
| new.target | 4/14 | 71% passing |
| Generators | ~30 | Various edge cases |

### Check Current Test Status

Use the test status script to see conformance by ECMAScript edition:

```bash
# Summary of all editions
node scripts/test-status.js

# Detailed ES6 report with failures by category
node scripts/test-status.js 6

# ES6 report with individual failing test paths
node scripts/test-status.js 6 --all
```

This shows:
- Pass rates by ECMAScript edition (ES5, ES6, ES2017, etc.)
- Failures broken down by category
- Categories with 100% pass rate

### Comparing Branches

To see improvements/regressions between branches:

```bash
# Compare current branch to master (all editions)
node scripts/test-status.js --diff master HEAD

# Compare ES6 only
node scripts/test-status.js --diff master HEAD 6
```

This shows pass rate changes and lists individual tests that improved or regressed.

### Strategy for Fixing Tests

1. Run `node scripts/test-status.js 6` to see ES6 status
2. Pick a category with fixable tests (avoid `{unsupported: [...]}` tests)
3. Look for patterns - fixing one issue often fixes many tests
4. After each fix, regenerate test262.properties and verify improvements
5. Commit and continue - don't stop until we hit 100%

### What to Prioritize

- **High-value fixes**: Issues that affect many tests (like function name inference)
- **Avoid**: Tests marked `{unsupported: [async-functions]}` etc. - these need new features
- **Focus on**: Pure ES6 syntax/semantics issues in the parser, IRFactory, or runtime

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