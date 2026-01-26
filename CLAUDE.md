@AGENTS.md

## Fixing test262 tests

When fixing test262 tests, you can regenerate the list of passing tests by running:

```
RHINO_TEST_JAVA_VERSION=11 ./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest --rerun-tasks -DupdateTest262properties
```

**IMPORTANT**: After regenerating, you MUST verify that the changes to test262.properties are removing failures (tests that now pass), NOT adding new failures. Check the git diff carefully. If there are new failures, you MUST confirm with me before proceeding - sometimes failures are expected but they need explicit approval.