/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests;

import static org.mozilla.javascript.drivers.TestUtils.JS_FILE_FILTER;
import static org.mozilla.javascript.drivers.TestUtils.recursiveListFilesHelper;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Kit;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.SymbolKey;
import org.mozilla.javascript.TopLevel;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.tools.SourceReader;
import org.mozilla.javascript.tools.shell.ShellContextFactory;
import org.mozilla.javascript.typedarrays.NativeArrayBuffer;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Standalone runner for test262 tests that outputs JSON in test262.fyi format.
 *
 * <p>Usage: java -cp <classpath> org.mozilla.javascript.tests.Test262JsonRunner --engine <name>
 * --output <dir> [--test262-path <path>] [--threads <n>]
 *
 * <p>A test passes if it passes in ANY of the 4 modes: interpreted-strict, interpreted-non-strict,
 * compiled-strict, compiled-non-strict.
 */
public class Test262JsonRunner {

    private static final String FLAG_RAW = "raw";
    private static final String FLAG_ONLY_STRICT = "onlyStrict";
    private static final String FLAG_NO_STRICT = "noStrict";

    // Timeout for each test execution (per mode) in seconds
    private static final int TEST_TIMEOUT_SECONDS = 10;

    private static final Map<String, Script> HARNESS_SCRIPT_CACHE = new ConcurrentHashMap<>();

    // Check if module support is available (fork has it, upstream doesn't)
    private static final boolean MODULES_SUPPORTED = checkModuleSupport();

    private static boolean checkModuleSupport() {
        try {
            Class.forName("org.mozilla.javascript.es6module.ModuleRecord");
            Context.class.getMethod(
                    "compileModule", String.class, String.class, int.class, Object.class);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return false;
        }
    }

    private static ShellContextFactory CTX_FACTORY =
            new ShellContextFactory() {
                @Override
                protected boolean hasFeature(Context cx, int featureIndex) {
                    if (Context.FEATURE_INTL_402 == featureIndex) {
                        return true;
                    }
                    return super.hasFeature(cx, featureIndex);
                }
            };

    static {
        CTX_FACTORY.setLanguageVersion(Context.VERSION_ES6);
    }

    private final File testDir;
    private final String testHarnessDir;
    private final String engineName;
    private final Path outputDir;
    private final int threadCount;

    private final AtomicInteger passCount = new AtomicInteger(0);
    private final AtomicInteger failCount = new AtomicInteger(0);
    private final AtomicInteger skipCount = new AtomicInteger(0);
    private final AtomicInteger timeoutCount = new AtomicInteger(0);

    // Thread-safe map to store results: path -> passed
    private final ConcurrentHashMap<String, Boolean> testResults = new ConcurrentHashMap<>();

    // Separate executor for running individual tests with timeout
    private ExecutorService testExecutor;

    public Test262JsonRunner(String test262Path, String engineName, String outputDir, int threads) {
        this.testDir = new File(test262Path, "test");
        this.testHarnessDir = test262Path + "/harness/";
        this.engineName = engineName;
        this.outputDir = Paths.get(outputDir);
        this.threadCount = threads;
    }

    public static void main(String[] args) throws Exception {
        String engineName = null;
        String outputDir = null;
        String test262Path = "test262";
        int threads = Runtime.getRuntime().availableProcessors();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--engine":
                    engineName = args[++i];
                    break;
                case "--output":
                    outputDir = args[++i];
                    break;
                case "--test262-path":
                    test262Path = args[++i];
                    break;
                case "--threads":
                    threads = Integer.parseInt(args[++i]);
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        if (engineName == null || outputDir == null) {
            printUsage();
            System.exit(1);
        }

        Test262JsonRunner runner =
                new Test262JsonRunner(test262Path, engineName, outputDir, threads);
        runner.run();
    }

    private static void printUsage() {
        System.err.println(
                "Usage: java org.mozilla.javascript.tests.Test262JsonRunner "
                        + "--engine <name> --output <dir> [--test262-path <path>] [--threads <n>]");
    }

    public void run() throws Exception {
        Files.createDirectories(outputDir);

        // Check if test directory exists
        if (!testDir.exists()) {
            throw new IllegalStateException(
                    "Test directory does not exist: "
                            + testDir.getAbsolutePath()
                            + "\nMake sure test262 submodule is initialized: git submodule update --init tests/test262");
        }
        if (!testDir.isDirectory()) {
            throw new IllegalStateException(
                    "Test path is not a directory: " + testDir.getAbsolutePath());
        }

        // Collect all test files
        List<File> testFiles = new LinkedList<>();
        recursiveListFilesHelper(testDir, JS_FILE_FILTER, testFiles);

        System.out.printf("Found %d test files%n", testFiles.size());
        System.out.printf(
                "Using %d threads with %d second timeout per test%n",
                threadCount, TEST_TIMEOUT_SECONDS);

        // Executor for running individual test modes with timeout
        testExecutor = Executors.newCachedThreadPool();

        // Run tests in parallel
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        int processed = 0;
        for (File testFile : testFiles) {
            final File f = testFile;
            futures.add(executor.submit(() -> runTest(f)));
            processed++;
            if (processed % 1000 == 0) {
                System.out.printf("Submitted %d/%d tests...%n", processed, testFiles.size());
            }
        }

        // Wait for all tests to complete
        executor.shutdown();
        int completed = 0;
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                System.err.printf("Test execution error: %s%n", e.getMessage());
            }
            completed++;
            if (completed % 1000 == 0) {
                System.out.printf(
                        "Completed %d/%d tests (pass=%d, fail=%d, skip=%d, timeout=%d)%n",
                        completed,
                        testFiles.size(),
                        passCount.get(),
                        failCount.get(),
                        skipCount.get(),
                        timeoutCount.get());
            }
        }
        executor.awaitTermination(1, TimeUnit.HOURS);
        testExecutor.shutdownNow();

        System.out.printf(
                "Final: %d passed, %d failed, %d skipped, %d timed out%n",
                passCount.get(), failCount.get(), skipCount.get(), timeoutCount.get());

        // Build hierarchical results and write JSON files
        writeResults();
    }

    private void runTest(File testFile) {
        String relativePath =
                testDir.toPath().relativize(testFile.toPath()).toString().replace("\\", "/");

        try {
            Test262Case testCase;
            try {
                testCase = Test262Case.fromSource(testFile, testHarnessDir);
            } catch (YAMLException | IOException ex) {
                System.err.printf("Error parsing %s: %s%n", relativePath, ex.getMessage());
                failCount.incrementAndGet();
                testResults.put(relativePath, false);
                return;
            }

            // Check for unsupported flags
            // modules require the fork's module support
            if (testCase.hasFlag("module") && !MODULES_SUPPORTED) {
                skipCount.incrementAndGet();
                return;
            }

            // Try all modes - pass if ANY succeeds
            boolean passed = false;
            boolean isModule = testCase.hasFlag("module");

            for (boolean interpreted : new boolean[] {true, false}) {
                if (passed) break;

                if (isModule) {
                    // Modules are always strict - only try once per interpretation mode
                    if (runSingleTest(testCase, relativePath, interpreted, true)) {
                        passed = true;
                        break;
                    }
                } else {
                    boolean canRunStrict =
                            !testCase.hasFlag(FLAG_NO_STRICT) && !testCase.hasFlag(FLAG_RAW);
                    boolean canRunNonStrict =
                            !testCase.hasFlag(FLAG_ONLY_STRICT) || testCase.hasFlag(FLAG_RAW);

                    if (canRunNonStrict) {
                        if (runSingleTest(testCase, relativePath, interpreted, false)) {
                            passed = true;
                            break;
                        }
                    }

                    if (canRunStrict) {
                        if (runSingleTest(testCase, relativePath, interpreted, true)) {
                            passed = true;
                            break;
                        }
                    }
                }
            }

            if (passed) {
                passCount.incrementAndGet();
            } else {
                failCount.incrementAndGet();
            }
            testResults.put(relativePath, passed);
        } catch (Exception e) {
            // Catch-all for any unexpected errors - count as failure
            System.err.printf("Unexpected error in %s: %s%n", relativePath, e.getMessage());
            failCount.incrementAndGet();
            testResults.put(relativePath, false);
        }
    }

    private boolean runSingleTest(
            Test262Case testCase, String testFilePath, boolean interpretedMode, boolean useStrict) {
        Future<Boolean> future =
                testExecutor.submit(
                        () ->
                                runSingleTestInternal(
                                        testCase, testFilePath, interpretedMode, useStrict));

        try {
            return future.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            timeoutCount.incrementAndGet();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean runSingleTestInternal(
            Test262Case testCase, String testFilePath, boolean interpretedMode, boolean useStrict) {
        try (Context cx = Context.enter()) {
            cx.setInterpretedMode(interpretedMode);
            cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
            cx.setGeneratingDebug(true);

            boolean failedEarly = false;
            boolean isModule = testCase.hasFlag("module");

            try {
                Scriptable scope = buildScope(cx, testCase, interpretedMode);
                String str = testCase.source;
                int line = 1;

                // Modules are always strict, don't prepend "use strict"
                if (useStrict && !isModule) {
                    str = "\"use strict\";\n" + str;
                    line--;
                }

                failedEarly = true;

                if (isModule && MODULES_SUPPORTED) {
                    // Use reflection to avoid compile-time dependency on module classes
                    // This allows the code to compile against upstream Rhino (no modules)
                    runModuleTest(cx, scope, testCase, str, line);
                    failedEarly = false;
                } else if (isModule) {
                    // Modules not supported - should have been skipped earlier
                    return false;
                } else {
                    Script caseScript = cx.compileString(str, testFilePath, line, null);

                    failedEarly = false;
                    caseScript.exec(cx, scope, scope);
                }

                // If we get here without exception, check if test expected an error
                if (testCase.isNegative()) {
                    return false; // Expected error but none thrown
                }
                return true; // Pass
            } catch (RhinoException ex) {
                if (!testCase.isNegative()) {
                    return false; // Unexpected error
                }

                String errorName = extractJSErrorName(ex);

                if (testCase.hasEarlyError && !failedEarly) {
                    return false; // Expected early error but got runtime error
                }

                // Check if error type matches
                return testCase.expectedError.equals(errorName);
            } catch (Exception | LinkageError ex) {
                return false;
            }
        }
    }

    /**
     * Run a module test using reflection to avoid compile-time dependency on module classes. This
     * allows the code to compile against upstream Rhino which doesn't have module support.
     */
    private void runModuleTest(
            Context cx, Scriptable scope, Test262Case testCase, String source, int line)
            throws Exception {
        File testFile = testCase.file;
        File testDir = testFile.getParentFile();

        // Create Test262ModuleLoader via reflection
        Class<?> loaderClass = Class.forName("org.mozilla.javascript.tests.Test262ModuleLoader");
        Object moduleLoader = loaderClass.getConstructor(File.class).newInstance(testDir);

        // Set the module loader on the context
        Context.class
                .getMethod(
                        "setModuleLoader",
                        Class.forName("org.mozilla.javascript.es6module.ModuleLoader"))
                .invoke(cx, moduleLoader);

        // Use the actual file path as the module specifier
        String moduleSpecifier = testFile.getAbsolutePath();

        // Compile the module
        Object moduleRecord =
                Context.class
                        .getMethod(
                                "compileModule",
                                String.class,
                                String.class,
                                int.class,
                                Object.class)
                        .invoke(cx, source, moduleSpecifier, line, null);

        // Cache the module so self-referential imports work
        loaderClass
                .getMethod(
                        "cacheModule",
                        String.class,
                        Class.forName("org.mozilla.javascript.es6module.ModuleRecord"))
                .invoke(moduleLoader, moduleSpecifier, moduleRecord);

        // Execute the module
        Context.class
                .getMethod(
                        "linkAndEvaluateModule",
                        Scriptable.class,
                        Class.forName("org.mozilla.javascript.es6module.ModuleRecord"))
                .invoke(cx, scope, moduleRecord);
    }

    private Scriptable buildScope(Context cx, Test262Case testCase, boolean interpretedMode) {
        ScriptableObject scope = (ScriptableObject) cx.initSafeStandardObjects(new TopLevel());

        for (String harnessFile : testCase.harnessFiles) {
            String harnessKey = harnessFile + '-' + interpretedMode;
            Script harnessScript;
            try {
                harnessScript =
                        HARNESS_SCRIPT_CACHE.computeIfAbsent(
                                harnessKey,
                                k -> {
                                    String harnessPath = testHarnessDir + harnessFile;
                                    try (Reader reader = new FileReader(harnessPath)) {
                                        String script = Kit.readReader(reader);
                                        return cx.compileString(script, harnessPath, 1, null);
                                    } catch (IOException ioe) {
                                        throw new RuntimeException(
                                                "Error reading harness file " + harnessPath, ioe);
                                    }
                                });
                harnessScript.exec(cx, scope, scope);
            } catch (Exception e) {
                throw new RuntimeException("Error loading harness " + harnessFile, e);
            }
        }

        $262 proto = $262.init(cx, scope);
        $262.install(scope, proto);
        return scope;
    }

    private static String extractJSErrorName(RhinoException ex) {
        if (ex instanceof EvaluatorException) {
            return "SyntaxError";
        }
        String exceptionName = ex.details();
        if (exceptionName.contains(":")) {
            exceptionName = exceptionName.substring(0, exceptionName.indexOf(":"));
        }
        return exceptionName;
    }

    private void writeResults() throws IOException {
        System.out.printf("Writing results to %s%n", outputDir);
        System.out.printf("Total test results to write: %d%n", testResults.size());

        // Build hierarchical structure
        Map<String, Object> rootFiles = new TreeMap<>();
        int totalTests = 0;
        int totalPassed = 0;

        for (Map.Entry<String, Boolean> entry : testResults.entrySet()) {
            String path = entry.getKey();
            if (path == null || path.isEmpty()) {
                continue; // Skip invalid paths
            }
            boolean passed = entry.getValue();
            totalTests++;
            if (passed) totalPassed++;

            // Split path and build hierarchy
            String[] parts = path.split("/");
            if (parts.length == 0) {
                continue; // Skip if no valid path parts
            }

            Map<String, Object> current = rootFiles;

            for (int i = 0; i < parts.length - 1; i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> next =
                        (Map<String, Object>)
                                current.computeIfAbsent(parts[i], k -> new TreeMap<>());
                current = next;
            }

            // Store test result with special key
            current.put("__test__" + parts[parts.length - 1], passed);
        }

        // Write index.json with aggregated counts
        Map<String, Object> index = new LinkedHashMap<>();
        index.put("total", totalTests);

        Map<String, Integer> engines = new LinkedHashMap<>();
        engines.put(engineName, totalPassed);
        index.put("engines", engines);

        Map<String, Object> files = new LinkedHashMap<>();
        aggregateResults(rootFiles, files);
        index.put("files", files);

        writeJsonFile(outputDir.resolve("index.json"), index);
        System.out.printf("Wrote index.json: total=%d, passed=%d%n", totalTests, totalPassed);

        // Write engines.json
        Map<String, String> enginesInfo = new LinkedHashMap<>();
        enginesInfo.put(engineName, getRhinoVersion());
        writeJsonFile(outputDir.resolve("engines.json"), enginesInfo);
        System.out.println("Wrote engines.json");

        // Write per-directory JSON files
        writeDirectoryJson(rootFiles, outputDir, "");
        System.out.println("Wrote all directory JSON files");
    }

    private void aggregateResults(Map<String, Object> source, Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("__test__")) {
                continue; // Skip individual tests at this level
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> subdir = (Map<String, Object>) entry.getValue();
            int[] counts = countTests(subdir);

            Map<String, Object> dirInfo = new LinkedHashMap<>();
            dirInfo.put("total", counts[0]);
            Map<String, Integer> eng = new LinkedHashMap<>();
            eng.put(engineName, counts[1]);
            dirInfo.put("engines", eng);

            target.put(key, dirInfo);
        }
    }

    private int[] countTests(Map<String, Object> dir) {
        int total = 0;
        int passed = 0;

        for (Map.Entry<String, Object> entry : dir.entrySet()) {
            if (entry.getKey().startsWith("__test__")) {
                total++;
                if ((Boolean) entry.getValue()) {
                    passed++;
                }
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> subdir = (Map<String, Object>) entry.getValue();
                int[] sub = countTests(subdir);
                total += sub[0];
                passed += sub[1];
            }
        }

        return new int[] {total, passed};
    }

    private void writeDirectoryJson(Map<String, Object> dir, Path parentPath, String dirName)
            throws IOException {
        for (Map.Entry<String, Object> entry : dir.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("__test__")) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> subdir = (Map<String, Object>) entry.getValue();

            // Create directory-level JSON
            Path dirPath = parentPath.resolve(key);
            Files.createDirectories(dirPath);

            // Write summary for this directory
            int[] counts = countTests(subdir);
            Map<String, Object> dirJson = new LinkedHashMap<>();
            dirJson.put("total", counts[0]);
            Map<String, Integer> eng = new LinkedHashMap<>();
            eng.put(engineName, counts[1]);
            dirJson.put("engines", eng);

            // Add files info
            Map<String, Object> files = new LinkedHashMap<>();
            aggregateResults(subdir, files);
            if (!files.isEmpty()) {
                dirJson.put("files", files);
            }

            // Add individual test results
            Map<String, Object> tests = new LinkedHashMap<>();
            for (Map.Entry<String, Object> testEntry : subdir.entrySet()) {
                if (testEntry.getKey().startsWith("__test__")) {
                    String testName = testEntry.getKey().substring(8); // Remove "__test__" prefix
                    Map<String, Boolean> testResult = new LinkedHashMap<>();
                    testResult.put(engineName, (Boolean) testEntry.getValue());
                    tests.put(testName, testResult);
                }
            }
            if (!tests.isEmpty()) {
                dirJson.put("tests", tests);
            }

            writeJsonFile(parentPath.resolve(key + ".json"), dirJson);

            // Recurse into subdirectories
            writeDirectoryJson(subdir, dirPath, key);
        }
    }

    private void writeJsonFile(Path path, Object data) throws IOException {
        try (FileWriter writer = new FileWriter(path.toFile())) {
            writeJson(writer, data, 0);
        }
    }

    private void writeJson(FileWriter writer, Object data, int indent) throws IOException {
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) data;
            writer.write("{\n");
            int i = 0;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                writeIndent(writer, indent + 1);
                writer.write("\"" + escapeJson(entry.getKey()) + "\": ");
                writeJson(writer, entry.getValue(), indent + 1);
                if (i < map.size() - 1) {
                    writer.write(",");
                }
                writer.write("\n");
                i++;
            }
            writeIndent(writer, indent);
            writer.write("}");
        } else if (data instanceof Collection) {
            @SuppressWarnings("unchecked")
            Collection<Object> list = (Collection<Object>) data;
            writer.write("[\n");
            int i = 0;
            for (Object item : list) {
                writeIndent(writer, indent + 1);
                writeJson(writer, item, indent + 1);
                if (i < list.size() - 1) {
                    writer.write(",");
                }
                writer.write("\n");
                i++;
            }
            writeIndent(writer, indent);
            writer.write("]");
        } else if (data instanceof String) {
            writer.write("\"" + escapeJson((String) data) + "\"");
        } else if (data instanceof Number) {
            writer.write(data.toString());
        } else if (data instanceof Boolean) {
            writer.write(data.toString());
        } else if (data == null) {
            writer.write("null");
        }
    }

    private void writeIndent(FileWriter writer, int indent) throws IOException {
        for (int i = 0; i < indent; i++) {
            writer.write("  ");
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String getRhinoVersion() {
        try (Context cx = Context.enter()) {
            return cx.getImplementationVersion();
        }
    }

    /** Host-defined $262 object for test262 tests. */
    public static class $262 extends ScriptableObject {

        $262() {
            super();
        }

        $262(Scriptable scope, Scriptable prototype) {
            super(scope, prototype);
        }

        static $262 init(Context cx, Scriptable scope) {
            $262 proto = new $262();
            proto.setPrototype(getObjectPrototype(scope));
            proto.setParentScope(scope);

            proto.defineProperty(scope, "gc", 0, $262::gc);
            proto.defineProperty(scope, "createRealm", 0, $262::createRealm);
            proto.defineProperty(scope, "evalScript", 1, $262::evalScript);
            proto.defineProperty(scope, "detachArrayBuffer", 0, $262::detachArrayBuffer);

            proto.defineProperty(cx, "global", $262::getGlobal, null, DONTENUM | READONLY);
            proto.defineProperty(cx, "agent", $262::getAgent, null, DONTENUM | READONLY);

            proto.defineProperty(SymbolKey.TO_STRING_TAG, "__262__", DONTENUM | READONLY);

            ScriptableObject.defineProperty(scope, "__262__", proto, DONTENUM);
            return proto;
        }

        static $262 install(ScriptableObject scope, Scriptable parentScope) {
            $262 instance = new $262(scope, parentScope);

            scope.put("$262", scope, instance);
            scope.setAttributes("$262", ScriptableObject.DONTENUM);

            return instance;
        }

        private static Object gc(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            System.gc();
            return Undefined.instance;
        }

        public static Object evalScript(
                Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            if (args.length == 0) {
                throw ScriptRuntime.throwError(cx, scope, "not enough args");
            }
            String source = Context.toString(args[0]);
            return cx.evaluateString(scope, source, "<evalScript>", 1, null);
        }

        public static Object getGlobal(Scriptable scriptable) {
            return scriptable.getParentScope();
        }

        public static $262 createRealm(
                Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            ScriptableObject realm = (ScriptableObject) cx.initSafeStandardObjects(new TopLevel());
            return install(realm, thisObj.getPrototype());
        }

        public static Object detachArrayBuffer(
                Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            Scriptable buf = ScriptRuntime.toObject(scope, args[0]);
            if (buf instanceof NativeArrayBuffer) {
                ((NativeArrayBuffer) buf).detach();
            }
            return Undefined.instance;
        }

        public static Object getAgent(Scriptable scriptable) {
            throw new UnsupportedOperationException("$262.agent property not yet implemented");
        }

        @Override
        public String getClassName() {
            return "__262__";
        }
    }

    /** Parsed test262 test case. */
    private static class Test262Case {
        private static final Yaml YAML = new Yaml();

        final File file;
        final String source;
        final String expectedError;
        final boolean hasEarlyError;
        final Set<String> flags;
        final List<String> harnessFiles;
        final Set<String> features;

        Test262Case(
                File file,
                String source,
                List<String> harnessFiles,
                String expectedError,
                boolean hasEarlyError,
                Set<String> flags,
                Set<String> features) {

            this.file = file;
            this.source = source;
            this.harnessFiles = harnessFiles;
            this.expectedError = expectedError;
            this.hasEarlyError = hasEarlyError;
            this.flags = flags;
            this.features = features;
        }

        boolean hasFlag(String flag) {
            return flags != null && flags.contains(flag);
        }

        boolean isNegative() {
            return expectedError != null;
        }

        @SuppressWarnings("unchecked")
        static Test262Case fromSource(File testFile, String testHarnessDir) throws IOException {
            String testSource =
                    (String) SourceReader.readFileOrUrl(testFile.getPath(), true, "UTF-8");

            List<String> harnessFiles = new ArrayList<>();
            Map<String, Object> metadata;

            if (testSource.indexOf("/*---") != -1) {
                String metadataStr =
                        testSource.substring(
                                testSource.indexOf("/*---") + 5, testSource.indexOf("---*/"));
                metadata = (Map<String, Object>) YAML.load(metadataStr);
                if (metadata == null) {
                    metadata = new HashMap<>();
                }
            } else {
                metadata = new HashMap<>();
            }

            String expectedError = null;
            boolean isEarly = false;
            if (metadata.containsKey("negative")) {
                Map<String, String> negative = (Map<String, String>) metadata.get("negative");
                expectedError = negative.get("type");
                isEarly = "early".equals(negative.get("phase"));
            }

            Set<String> flags = new HashSet<>();
            if (metadata.containsKey("flags")) {
                flags.addAll((Collection<String>) metadata.get("flags"));
            }

            Set<String> features = new HashSet<>();
            if (metadata.containsKey("features")) {
                features.addAll((Collection<String>) metadata.get("features"));
            }

            if (flags.contains(FLAG_RAW) && metadata.containsKey("includes")) {
                // Raw tests shouldn't have includes
            } else {
                harnessFiles.add("assert.js");
                harnessFiles.add("sta.js");

                if (metadata.containsKey("includes")) {
                    harnessFiles.addAll((List<String>) metadata.get("includes"));
                }
            }

            return new Test262Case(
                    testFile, testSource, harnessFiles, expectedError, isEarly, flags, features);
        }
    }
}
