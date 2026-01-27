@AGENTS.md

## Fixing test262 tests

### Understanding test262.properties format

The `test262.properties` file shows **failure rates** and lists **failing tests**:
- `built-ins/Error 3/53 (5.66%)` means 3 failures out of 53 tests (5.66% failure rate) - so 50 tests pass
- `built-ins/Infinity 0/6 (0.0%)` means 0 failures out of 6 tests (0% failure rate) - all 6 tests pass
- Indented lines below a category are the **failing** tests
- Categories with 100% failure rate have all tests failing
- Categories prefixed with `~` are completely skipped
- Tests marked with `{unsupported: [...]}` require features Rhino doesn't support

### Running specific test262 tests

Use the `-Dtest262filter` system property with glob patterns to run specific tests:

```bash
# Run all tests in a specific category
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest -Dtest262filter="built-ins/Array/*"

# Run a specific test file
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest -Dtest262filter="built-ins/Array/prototype/map/*"

# Run tests matching a pattern
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest -Dtest262filter="**/prop-desc.js"
```

The filter supports `*` (matches any characters) and `?` (matches single character) wildcards.

### Regenerating the properties file

When fixing test262 tests, you can regenerate the list of passing tests by running:

```
RHINO_TEST_JAVA_VERSION=11 ./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest --rerun-tasks -DupdateTest262properties
```

**IMPORTANT**: After regenerating, you MUST verify that the changes to test262.properties are removing failures (tests that now pass), NOT adding new failures. Check the git diff carefully. If there are new failures, you MUST confirm with me before proceeding - sometimes failures are expected but they need explicit approval.