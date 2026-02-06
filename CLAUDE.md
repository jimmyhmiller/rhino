@AGENTS.md

## TOP PRIORITY: Language Feature Completion

**GOAL: Get test262 pass rate higher**

We're focusing on completing JavaScript language feature support.

### Current Status (2026-02-05)

```
Total         ~37,563 / 50,756 passing (~74.0%)
```

**Recent Fixes:**
- Optional chaining + private fields (`obj?.#field`, `o?.c.#field`) - 4 tests
- Duplicate private name detection (getter+setter pair allowed, others rejected) - 26 tests
- `#constructor` private name disallowed (including static methods) - 12 tests
- Private name escape validation (ZWNJ/ZWJ/NULL rejected at IdentifierStart) - 6 tests
- `fields-duplicate-privatenames` runtime duplicate detection - 2 tests
- Parser: AllPrivateNamesValid validation (undeclared #names → SyntaxError) - 52 tests
- Parser: Escaped contextual keywords (get/set/async/static) rejected per spec - 13 tests
- Parser: Class field ASI enforcement (same-line errors) - 6 tests
- TokenStream: ZWNJ/ZWJ preserved in identifiers (not stripped as format chars) - 12 tests
- Interpreter: static block literal storage sizing - 24 tests
- Interpreter: computed numeric keys preserved in NewLiteralStorage - 80+ tests
- Private fields: null values handled via UniqueTag.NULL_VALUE sentinel - 1 test
- Private accessor methods: homeObject set correctly for super access - 18 tests

**Next Priority Items:**
1. **Named function expression immutable binding** - ~18 tests (reassignment silently ignored)
2. **Optional chaining with private fields** (`obj?.#field`) - parser/runtime support needed
3. **eval code edge cases** - 184 tests (53% failing)

---

## Major Missing Language Features (by impact)

### 1. Dynamic Import - 626 tests (100% failing)
```javascript
// NOT WORKING
const module = await import('./module.js');
```
- `import()` expression not implemented
- Subcategories: syntax (192), usage (108), catch (176), namespace (67)

### 2. Private Field Compound/Logical Assignment - FIXED
- Compound and logical assignment now work
- Only remaining issue: `#field = null` with `??=` (1 test, fixed via UniqueTag.NULL_VALUE)

### 3. Module Code Issues - 297 tests (51% failing)
- Top-level await (~200 tests)
- Import attributes
- Import defer

### 4. Class Remaining Issues - ~1,538 tests (18% failing)
- Decorator syntax (11 tests, Stage 3)
- Compiled-mode yield-spread-obj crashes (24 tests)

### 5. Eval Code Edge Cases - 184 tests (53% failing)
- `arguments` binding in various contexts

---

## Quick Wins (Recommended Order)

| Feature | Tests | Difficulty |
|---------|-------|------------|
| **Named function expression binding** | ~18 | Medium - immutable binding in non-strict mode |
| **yield-spread-obj compiled crashes** | 24 | Medium - compiler crash with yield in spread |

---

## What's Already Working

| Feature | Status |
|---------|--------|
| Classes (methods, fields, private, static) | ✅ 81% |
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
| Logical assignment (`&&=`, `||=`, `??=`) | ✅ Works |

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
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest --rerun-tasks -DupdateTest262properties
```

**IMPORTANT**: After regenerating, you MUST verify that the changes to test262.properties are removing failures (tests that now pass), NOT adding new failures. Check the git diff carefully. If there are new failures, you MUST confirm with me before proceeding - sometimes failures are expected but they need explicit approval.
