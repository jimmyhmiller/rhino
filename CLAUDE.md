@AGENTS.md

## TOP PRIORITY: 100% ES6 Test262 Conformance

**THIS IS THE #1 PRIORITY. We will not stop until ALL ES6 tests pass.**

The goal is to achieve 100% passing rate for ES6 test262 tests. This is non-negotiable - keep fixing tests until there are zero failures.

### ✅ ES6 Modules: COMPLETE (49/49 tests passing)

All ES6 module tests now pass! See [docs/ES6_MODULES.md](docs/ES6_MODULES.md) for implementation details.

### Recently Fixed TDZ Tests

Fixed the last 3 ES6 module tests related to Temporal Dead Zone (TDZ) handling for module namespace objects:

| Test | Fix |
|------|-----|
| `enumerate-binding-uninit.js` | TDZ check in `ScriptRuntime.enumNext()` for module namespace for-in |
| `object-keys-binding-uninit.js` | TDZ check in `NativeObject.js_keys()` for module namespaces |
| `object-propertyIsEnumerable-binding-uninit.js` | TDZ check via `getAttributes()` + `isEnumerable()` re-throwing ReferenceError |

Also fixed `NativeObject.isEnumerable()` to re-throw `ReferenceError` and `TypeError` instead of swallowing them.

---

### NEXT PRIORITY: ES6 Class Tests (253 failures)

**Total ES6 class test failures: 253** (from `node scripts/test-status.js 6 --all | grep class/`)

#### Breakdown by Category

| Category | Count | Description |
|----------|-------|-------------|
| `dstr/gen-meth-*` | **208** | Generator method destructuring errors |
| `dflt-params-ref-*` | 16 | Default parameter reference issues |
| `*yield*` | 8 | Yield in computed property names (codegen bug) |
| `dstr/meth-*-eval-err` | 8 | Method destructuring eval errors |
| `*async-arrow*` | 5 | Async arrow expressions (may be ES2017+) |
| `params-dflt-meth-ref-arguments` | 4 | Default params referencing arguments |
| `scope-*-paramsbody-var-open` | 4 | Generator method scope issues |
| `heritage-async-arrow` | 1 | Async arrow in class heritage (ES2017+) |

#### Priority 1: Generator Method Destructuring (208 tests - 82% of failures)

These test error handling when destructuring fails in generator methods:

```javascript
class C {
  *method([x]) {}  // destructuring in generator method
}
var iter = { [Symbol.iterator]: () => { throw new Test262Error(); } };
c.method(iter);  // should propagate the error correctly
```

**Root Cause Investigation (2026-02-03):**
- ES6 requires parameter destructuring during `FunctionDeclarationInstantiation` (when function is called), NOT when generator body runs
- Current Rhino behavior: destructuring happens when `next()` is called (deferred)
- Expected behavior: destructuring errors should throw immediately when `c.method(iter)` is called

**Attempted Fix (2026-02-03, reverted):**
- Moved destructuring from function body to `paramInitBlock` in `IRFactory.transformFunction()` (processed before `Icode_GENERATOR`)
- Added `NodeTransformer` handling to transform `paramInitBlock` LETEXPR nodes to WITHEXPR
- **Result:** Fix works correctly in **compiled mode** (opt level >= 0)
- **Issue:** Breaks **interpreted mode** (opt level -1) - `ArrayIndexOutOfBoundsException` when resuming generators
- **Why:** The WITHEXPR transformation creates scope objects using locals. When the generator frame is saved at `Icode_GENERATOR`, the frame's `savedStackTop` is captured. When the generator resumes (on `next()` call), the stack indices don't match what the generator body expects, causing overflow.

**Verified Behavior:**
```bash
# Compiled mode (works): java -cp ... Main -opt 9 -e "class C { *m([x=(function(){})]) { return x.name; } }; new C().m([]).next();"
# Interpreted mode (fails): java -cp ... Main -opt -1 -e "..." → ArrayIndexOutOfBoundsException
```

**Proper Fix Requires:**
1. Fix interpreter's generator frame capture in `Interpreter.captureFrameForGenerator()` to properly account for locals created in `paramInitBlock`
2. Or, generate simpler destructuring code for generator parameters that doesn't use LETEXPR/WITHEXPR scopes
3. Or, restructure generator initialization to run destructuring synchronously before returning generator object

**Test patterns:**
- `dstr/gen-meth-ary-*` - Array destructuring errors
- `dstr/gen-meth-obj-*` - Object destructuring errors
- `dstr/gen-meth-dflt-*` - Default value errors
- `dstr/gen-meth-static-*` - Static generator methods

**Run these tests:**
```bash
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="language/*/class/dstr/gen-meth-ary-init-iter-get-err.js" -Dtest262raw --rerun-tasks
```

#### Priority 2: Default Parameter Reference Issues (16 tests)

Parameters referencing later parameters or themselves:

```javascript
class C {
  method(x = y, y) {}     // dflt-params-ref-later - x references y before y is defined
  method(x = x) {}        // dflt-params-ref-self - x references itself
}
```

**Test files:** `*/class/method*/dflt-params-ref-later.js`, `*/class/method*/dflt-params-ref-self.js`

#### Priority 3: Yield in Computed Property Names (8 tests)

Classes inside generators using `yield` as computed property name - **COMPILED mode only bug**:

```javascript
function* g() {
  C = class {
    get [yield]() { return 'get yield'; }
  };
}
```

**Error:** `VerifyError: Bad type on operand stack` in compiled mode
**Location:** `Codegen.java` - generator/class interaction

**Test files:** `accessor-name-*-computed-yield-expr.js`, `cpn-class-*-from-yield-expression.js`

#### Run ES6 Class Tests

```bash
# See all ES6 class failures
node scripts/test-status.js 6 --all | grep class/

# Run specific category
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="language/*/class/dstr/gen-meth-*" -Dtest262raw --rerun-tasks

# Run yield-related tests
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="language/*/class/*yield*.js" -Dtest262raw --rerun-tasks
```

---

### Other ES6 Gaps (lower priority)

**new.target** - 10/14 tests passing (71%). 4 remaining failures need investigation.

**Other categories with failures:**
- `built-ins/Promise` - 71/114 failing (62%)
- `language/statements` - 504/2885 failing (17.5%)
- `language/expressions` - 390/2833 failing (13.8%)

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