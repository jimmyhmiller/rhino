@AGENTS.md

## TOP PRIORITY: 100% ES6 Test262 Conformance

**THIS IS THE #1 PRIORITY. We will not stop until ALL ES6 tests pass.**

The goal is to achieve 100% passing rate for ES6 test262 tests. This is non-negotiable - keep fixing tests until there are zero failures.

### Current Work In Progress

**ES6 Modules** - Parser complete, runtime execution not yet implemented (~672 skipped tests).
- ✅ Parser supports all import/export syntax
- ✅ AST nodes, IRFactory stubs, runtime infrastructure classes
- ❌ Module loading, linking, and evaluation (requires `ModuleLoader` implementation)
- See **[docs/ES6_MODULES.md](docs/ES6_MODULES.md)** for full implementation details
- **IMPORTANT**: Keep `docs/ES6_MODULES.md` updated as you make progress on module execution

**new.target** - Completely skipped (14 tests in `language/expressions/new.target`). This is an ES6 meta-property for detecting constructor calls.

**Other major ES6 gaps:**
- `built-ins/Promise` - 71/114 failing (62%)
- `language/statements` - 504/2885 failing (17.5%)
- `language/expressions` - 410/2833 failing (14.5%)

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