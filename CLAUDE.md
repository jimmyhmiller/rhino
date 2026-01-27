@AGENTS.md

## Fixing test262 tests

### Understanding test262.properties format

The `test262.properties` file shows **pass rates** and lists **failing tests**:
- `built-ins/Error 3/53 (5.66%)` means 3 out of 53 tests pass (5.66% pass rate)
- Indented lines below a category are the **failing** tests
- Categories with 0% pass rate (e.g., `built-ins/isFinite 0/15 (0.0%)`) have all tests failing
- Categories prefixed with `~` are completely skipped
- Tests marked with `{unsupported: [...]}` require features Rhino doesn't support

### Regenerating the properties file

When fixing test262 tests, you can regenerate the list of passing tests by running:

```
RHINO_TEST_JAVA_VERSION=11 ./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest --rerun-tasks -DupdateTest262properties
```

**IMPORTANT**: After regenerating, you MUST verify that the changes to test262.properties are removing failures (tests that now pass), NOT adding new failures. Check the git diff carefully. If there are new failures, you MUST confirm with me before proceeding - sometimes failures are expected but they need explicit approval.