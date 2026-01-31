@AGENTS.md

## Current Goal: 100% ES6 (ES2015) Test262 Pass Rate

The primary goal is to achieve 100% passing rate for ES6-only test262 tests. Use the edition analysis script to track progress:

```bash
# See current pass rates by edition
node scripts/list-tests-by-edition.js

# List all failing ES6 tests
node scripts/list-tests-by-edition.js -e 6 -s failing

# Get ES6 failing tests as JSON
node scripts/list-tests-by-edition.js -e 6 -s failing -o json
```

### Current ES6 Failure Categories (prioritized)

1. **class** (796 tests) - Pure ES6 class syntax
2. **for-of** (252 tests) - Iteration protocol
3. **generators** (214 tests) - Generator functions
4. **object expressions** (176 tests) - Computed properties, shorthand methods
5. **destructuring/assignment** (115 tests)
6. **arrow-function** (65 tests)

See [CLASSES_IMPLEMENTATION_PLAN.md](CLASSES_IMPLEMENTATION_PLAN.md) for class-specific work.

## Understanding ECMAScript Editions in test262

Tests are categorized by the **highest edition** of any feature they use. For example:
- A test using only `class` (ES6) and `const` (ES6) → ES6
- A test using `class` (ES6) and `class-fields-private` (ES2022) → ES2022

The script `scripts/list-tests-by-edition.js` uses the official feature-to-edition mapping from [test262-fyi](https://github.com/test262-fyi/test262.fyi).

### Edition Analysis Script

```bash
# Summary of all editions
node scripts/list-tests-by-edition.js

# All tests for a specific edition
node scripts/list-tests-by-edition.js -e 6           # ES6
node scripts/list-tests-by-edition.js -e 13          # ES2022

# Filter by status
node scripts/list-tests-by-edition.js -e 6 -s failing
node scripts/list-tests-by-edition.js -e 6 -s passing

# Output as JSON
node scripts/list-tests-by-edition.js -e 6 -s failing -o json
```

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