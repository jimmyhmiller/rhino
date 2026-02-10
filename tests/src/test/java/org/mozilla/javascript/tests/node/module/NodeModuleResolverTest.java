/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests.node.module;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.node.module.NodeConditions;
import org.mozilla.javascript.node.module.NodeModuleResolver;
import org.mozilla.javascript.node.module.PackageJsonReader;
import org.mozilla.javascript.node.module.ResolvedModule;

class NodeModuleResolverTest {

    private MockNodeFileSystem fs;
    private PackageJsonReader pkgReader;
    private NodeModuleResolver resolver;

    @BeforeEach
    void setUp() {
        fs = new MockNodeFileSystem();
        pkgReader = new PackageJsonReader(fs);
        resolver = new NodeModuleResolver(fs, pkgReader);
    }

    @Nested
    class LoadAsFile {

        @Test
        void exactMatch() {
            fs.addFile("/project/lib/utils.js", "");
            assertEquals("/project/lib/utils.js", resolver.loadAsFile("/project/lib/utils.js"));
        }

        @Test
        void appendsJsExtension() {
            fs.addFile("/project/lib/utils.js", "");
            assertEquals("/project/lib/utils.js", resolver.loadAsFile("/project/lib/utils"));
        }

        @Test
        void appendsJsonExtension() {
            fs.addFile("/project/data.json", "{}");
            assertEquals("/project/data.json", resolver.loadAsFile("/project/data"));
        }

        @Test
        void prefersExactOverExtension() {
            fs.addFile("/project/lib/utils", "exact");
            fs.addFile("/project/lib/utils.js", "js");
            assertEquals("/project/lib/utils", resolver.loadAsFile("/project/lib/utils"));
        }

        @Test
        void prefersJsOverJson() {
            fs.addFile("/project/lib/config.js", "");
            fs.addFile("/project/lib/config.json", "{}");
            assertEquals("/project/lib/config.js", resolver.loadAsFile("/project/lib/config"));
        }

        @Test
        void returnsNullWhenNotFound() {
            assertNull(resolver.loadAsFile("/project/missing"));
        }
    }

    @Nested
    class LoadAsDirectory {

        @Test
        void usesPackageJsonMain() {
            fs.addFile("/project/node_modules/foo/package.json", "{\"main\": \"./lib/index.js\"}");
            fs.addFile("/project/node_modules/foo/lib/index.js", "");
            assertEquals(
                    "/project/node_modules/foo/lib/index.js",
                    resolver.loadAsDirectory("/project/node_modules/foo"));
        }

        @Test
        void fallsBackToIndexJs() {
            fs.addFile("/project/node_modules/foo/package.json", "{\"name\": \"foo\"}");
            fs.addFile("/project/node_modules/foo/index.js", "");
            assertEquals(
                    "/project/node_modules/foo/index.js",
                    resolver.loadAsDirectory("/project/node_modules/foo"));
        }

        @Test
        void indexJsWithoutPackageJson() {
            fs.addDirectory("/project/node_modules/foo");
            fs.addFile("/project/node_modules/foo/index.js", "");
            assertEquals(
                    "/project/node_modules/foo/index.js",
                    resolver.loadAsDirectory("/project/node_modules/foo"));
        }

        @Test
        void indexJsonFallback() {
            fs.addDirectory("/project/node_modules/foo");
            fs.addFile("/project/node_modules/foo/index.json", "{}");
            assertEquals(
                    "/project/node_modules/foo/index.json",
                    resolver.loadAsDirectory("/project/node_modules/foo"));
        }

        @Test
        void packageMainWithExtensionResolution() {
            fs.addFile("/project/node_modules/foo/package.json", "{\"main\": \"./lib/index\"}");
            fs.addFile("/project/node_modules/foo/lib/index.js", "");
            assertEquals(
                    "/project/node_modules/foo/lib/index.js",
                    resolver.loadAsDirectory("/project/node_modules/foo"));
        }

        @Test
        void returnsNullForNonDirectory() {
            assertNull(resolver.loadAsDirectory("/project/missing"));
        }
    }

    @Nested
    class NodeModulesPaths {

        @Test
        void generatesCorrectPaths() {
            List<String> paths = resolver.nodeModulesPaths("/project/src/lib");
            assertEquals("/project/src/lib/node_modules", paths.get(0));
            assertEquals("/project/src/node_modules", paths.get(1));
            assertEquals("/project/node_modules", paths.get(2));
            assertEquals("/node_modules", paths.get(3));
        }

        @Test
        void skipsNodeModulesDirectories() {
            List<String> paths = resolver.nodeModulesPaths("/project/node_modules/foo");
            // Should skip the node_modules level
            assertFalse(
                    paths.contains("/project/node_modules/node_modules"),
                    "Should skip node_modules/node_modules");
            assertTrue(paths.contains("/project/node_modules/foo/node_modules"));
            assertTrue(paths.contains("/project/node_modules"));
        }
    }

    @Nested
    class CjsResolve {

        @Test
        void resolvesRelativeFile() {
            fs.addFile("/project/src/utils.js", "");
            ResolvedModule result = resolver.cjsResolve("./utils", "/project/src/index.js");
            assertEquals("/project/src/utils.js", result.getPath());
            assertEquals(ResolvedModule.ModuleFormat.COMMONJS, result.getFormat());
        }

        @Test
        void resolvesRelativeWithExtension() {
            fs.addFile("/project/src/utils.js", "");
            ResolvedModule result = resolver.cjsResolve("./utils.js", "/project/src/index.js");
            assertEquals("/project/src/utils.js", result.getPath());
        }

        @Test
        void resolvesRelativeDirectory() {
            fs.addDirectory("/project/src/lib");
            fs.addFile("/project/src/lib/index.js", "");
            ResolvedModule result = resolver.cjsResolve("./lib", "/project/src/index.js");
            assertEquals("/project/src/lib/index.js", result.getPath());
        }

        @Test
        void resolvesParentRelative() {
            fs.addFile("/project/utils.js", "");
            ResolvedModule result = resolver.cjsResolve("../utils", "/project/src/index.js");
            assertEquals("/project/utils.js", result.getPath());
        }

        @Test
        void resolvesBareSpecifier() {
            fs.addFile("/project/node_modules/lodash/package.json", "{\"main\": \"./lodash.js\"}");
            fs.addFile("/project/node_modules/lodash/lodash.js", "");
            ResolvedModule result = resolver.cjsResolve("lodash", "/project/src/index.js");
            assertEquals("/project/node_modules/lodash/lodash.js", result.getPath());
        }

        @Test
        void resolvesScopedPackage() {
            fs.addFile(
                    "/project/node_modules/@scope/pkg/package.json", "{\"main\": \"./main.js\"}");
            fs.addFile("/project/node_modules/@scope/pkg/main.js", "");
            ResolvedModule result = resolver.cjsResolve("@scope/pkg", "/project/src/index.js");
            assertEquals("/project/node_modules/@scope/pkg/main.js", result.getPath());
        }

        @Test
        void resolvesSubpathOfPackage() {
            fs.addFile("/project/node_modules/lodash/package.json", "{\"name\": \"lodash\"}");
            fs.addFile("/project/node_modules/lodash/fp/index.js", "");
            ResolvedModule result = resolver.cjsResolve("lodash/fp", "/project/src/index.js");
            assertEquals("/project/node_modules/lodash/fp/index.js", result.getPath());
        }

        @Test
        void resolvesJsonFile() {
            fs.addFile("/project/data.json", "{}");
            ResolvedModule result = resolver.cjsResolve("./data.json", "/project/index.js");
            assertEquals("/project/data.json", result.getPath());
            assertEquals(ResolvedModule.ModuleFormat.JSON, result.getFormat());
        }

        @Test
        void throwsForMissingModule() {
            assertThrows(
                    NodeModuleResolver.ModuleNotFoundException.class,
                    () -> resolver.cjsResolve("missing", "/project/index.js"));
        }

        @Test
        void throwsForMissingRelative() {
            assertThrows(
                    NodeModuleResolver.ModuleNotFoundException.class,
                    () -> resolver.cjsResolve("./missing", "/project/index.js"));
        }

        @Test
        void resolvesNestedNodeModules() {
            // dep-a has its own node_modules with dep-b
            fs.addFile("/project/node_modules/dep-a/package.json", "{\"main\": \"./index.js\"}");
            fs.addFile("/project/node_modules/dep-a/index.js", "");
            fs.addFile(
                    "/project/node_modules/dep-a/node_modules/dep-b/package.json",
                    "{\"main\": \"./index.js\"}");
            fs.addFile("/project/node_modules/dep-a/node_modules/dep-b/index.js", "");

            // From dep-a, resolve dep-b (should find nested)
            ResolvedModule result =
                    resolver.cjsResolve("dep-b", "/project/node_modules/dep-a/index.js");
            assertEquals(
                    "/project/node_modules/dep-a/node_modules/dep-b/index.js", result.getPath());
        }
    }

    @Nested
    class EsmResolve {

        @Test
        void resolvesRelativeFile() {
            fs.addFile("/project/src/utils.js", "");
            ResolvedModule result = resolver.esmResolve("./utils.js", "/project/src/index.mjs");
            assertEquals("/project/src/utils.js", result.getPath());
        }

        @Test
        void resolvesRelativeWithExtensionAppending() {
            fs.addFile("/project/src/utils.js", "");
            ResolvedModule result = resolver.esmResolve("./utils", "/project/src/index.mjs");
            assertEquals("/project/src/utils.js", result.getPath());
        }

        @Test
        void resolvesMjsFormat() {
            fs.addFile("/project/src/utils.mjs", "");
            ResolvedModule result = resolver.esmResolve("./utils.mjs", "/project/src/index.mjs");
            assertEquals("/project/src/utils.mjs", result.getPath());
            assertEquals(ResolvedModule.ModuleFormat.MODULE, result.getFormat());
        }

        @Test
        void resolvesBareSpecifierWithExports() {
            fs.addFile(
                    "/project/node_modules/lib/package.json",
                    "{\"name\": \"lib\", \"exports\": {\".\": {\"import\": \"./esm/index.mjs\", \"require\": \"./cjs/index.js\"}}}");
            fs.addFile("/project/node_modules/lib/esm/index.mjs", "");
            fs.addFile("/project/node_modules/lib/cjs/index.js", "");

            ResolvedModule result = resolver.esmResolve("lib", "/project/src/index.mjs");
            assertEquals("/project/node_modules/lib/esm/index.mjs", result.getPath());
            assertEquals(ResolvedModule.ModuleFormat.MODULE, result.getFormat());
        }

        @Test
        void resolvesBareSpecifierFallsBackToMain() {
            fs.addFile(
                    "/project/node_modules/simple/package.json",
                    "{\"name\": \"simple\", \"main\": \"./index.js\"}");
            fs.addFile("/project/node_modules/simple/index.js", "");

            ResolvedModule result = resolver.esmResolve("simple", "/project/src/index.mjs");
            assertEquals("/project/node_modules/simple/index.js", result.getPath());
        }

        @Test
        void throwsForMissingModule() {
            assertThrows(
                    NodeModuleResolver.ModuleNotFoundException.class,
                    () -> resolver.esmResolve("missing", "/project/index.mjs"));
        }

        @Test
        void esmFileFormatMjs() {
            assertEquals(ResolvedModule.ModuleFormat.MODULE, resolver.esmFileFormat("/a.mjs"));
        }

        @Test
        void esmFileFormatCjs() {
            assertEquals(ResolvedModule.ModuleFormat.COMMONJS, resolver.esmFileFormat("/a.cjs"));
        }

        @Test
        void esmFileFormatJson() {
            assertEquals(ResolvedModule.ModuleFormat.JSON, resolver.esmFileFormat("/a.json"));
        }

        @Test
        void esmFileFormatJsWithModuleType() {
            fs.addFile("/project/package.json", "{\"type\": \"module\"}");
            assertEquals(
                    ResolvedModule.ModuleFormat.MODULE,
                    resolver.esmFileFormat("/project/index.js"));
        }

        @Test
        void esmFileFormatJsWithCommonjsType() {
            fs.addFile("/project/package.json", "{\"type\": \"commonjs\"}");
            assertEquals(
                    ResolvedModule.ModuleFormat.COMMONJS,
                    resolver.esmFileFormat("/project/index.js"));
        }

        @Test
        void esmFileFormatJsDefaultsToCommonjs() {
            assertEquals(
                    ResolvedModule.ModuleFormat.COMMONJS,
                    resolver.esmFileFormat("/project/index.js"));
        }
    }

    @Nested
    class PackageExportsResolve {

        @Test
        void stringExportsForDot() {
            fs.addFile("/pkg/main.js", "");
            ResolvedModule result =
                    resolver.packageExportsResolve(
                            "/pkg", ".", "./main.js", NodeConditions.CJS_CONDITIONS);
            assertNotNull(result);
            assertEquals("/pkg/main.js", result.getPath());
        }

        @Test
        void subpathExports() {
            fs.addFile("/pkg/lib/utils.js", "");
            Object exports =
                    new java.util.LinkedHashMap<String, Object>() {
                        {
                            put(".", "./main.js");
                            put("./utils", "./lib/utils.js");
                        }
                    };
            ResolvedModule result =
                    resolver.packageExportsResolve(
                            "/pkg", "./utils", exports, NodeConditions.CJS_CONDITIONS);
            assertNotNull(result);
            assertEquals("/pkg/lib/utils.js", result.getPath());
        }

        @Test
        void conditionalExports() {
            fs.addFile("/pkg/esm.mjs", "");
            fs.addFile("/pkg/cjs.js", "");
            Object exports =
                    new java.util.LinkedHashMap<String, Object>() {
                        {
                            put("import", "./esm.mjs");
                            put("require", "./cjs.js");
                        }
                    };
            // ESM conditions should pick "import"
            ResolvedModule esmResult =
                    resolver.packageExportsResolve(
                            "/pkg", ".", exports, NodeConditions.ESM_CONDITIONS);
            assertNotNull(esmResult);
            assertEquals("/pkg/esm.mjs", esmResult.getPath());

            // CJS conditions should pick "require"
            ResolvedModule cjsResult =
                    resolver.packageExportsResolve(
                            "/pkg", ".", exports, NodeConditions.CJS_CONDITIONS);
            assertNotNull(cjsResult);
            assertEquals("/pkg/cjs.js", cjsResult.getPath());
        }

        @Test
        void patternExports() {
            fs.addFile("/pkg/lib/utils.js", "");
            fs.addFile("/pkg/lib/helpers.js", "");
            Object exports =
                    new java.util.LinkedHashMap<String, Object>() {
                        {
                            put("./*", "./lib/*.js");
                        }
                    };
            ResolvedModule result =
                    resolver.packageExportsResolve(
                            "/pkg", "./utils", exports, NodeConditions.CJS_CONDITIONS);
            assertNotNull(result);
            assertEquals("/pkg/lib/utils.js", result.getPath());
        }

        @Test
        void arrayFallback() {
            fs.addFile("/pkg/fallback.js", "");
            Object exports =
                    new java.util.ArrayList<Object>() {
                        {
                            add("./missing.js");
                            add("./fallback.js");
                        }
                    };
            ResolvedModule result =
                    resolver.packageExportsResolve(
                            "/pkg", ".", exports, NodeConditions.CJS_CONDITIONS);
            assertNotNull(result);
            assertEquals("/pkg/fallback.js", result.getPath());
        }

        @Test
        void nullExportsBlocksSubpath() {
            Object exports =
                    new java.util.LinkedHashMap<String, Object>() {
                        {
                            put(".", "./main.js");
                            put("./internal", null);
                        }
                    };
            ResolvedModule result =
                    resolver.packageExportsResolve(
                            "/pkg", "./internal", exports, NodeConditions.CJS_CONDITIONS);
            assertNull(result);
        }
    }

    @Nested
    class PackageImportsResolve {

        @Test
        void resolvesSingleImport() {
            fs.addFile(
                    "/project/package.json",
                    "{\"name\": \"test\", \"imports\": {\"#internal\": \"./src/internal.js\"}}");
            fs.addFile("/project/src/internal.js", "");

            ResolvedModule result =
                    resolver.packageImportsResolve(
                            "#internal", "/project/src/index.js", NodeConditions.CJS_CONDITIONS);
            assertEquals("/project/src/internal.js", result.getPath());
        }

        @Test
        void resolvesPatternImport() {
            fs.addFile(
                    "/project/package.json",
                    "{\"name\": \"test\", \"imports\": {\"#lib/*\": \"./src/lib/*.js\"}}");
            fs.addFile("/project/src/lib/utils.js", "");

            ResolvedModule result =
                    resolver.packageImportsResolve(
                            "#lib/utils", "/project/src/index.js", NodeConditions.CJS_CONDITIONS);
            assertEquals("/project/src/lib/utils.js", result.getPath());
        }

        @Test
        void throwsForHashOnly() {
            fs.addFile(
                    "/project/package.json",
                    "{\"name\": \"test\", \"imports\": {\"#a\": \"./a.js\"}}");
            assertThrows(
                    NodeModuleResolver.ModuleNotFoundException.class,
                    () ->
                            resolver.packageImportsResolve(
                                    "#", "/project/index.js", NodeConditions.CJS_CONDITIONS));
        }

        @Test
        void throwsForHashSlash() {
            fs.addFile(
                    "/project/package.json",
                    "{\"name\": \"test\", \"imports\": {\"#a\": \"./a.js\"}}");
            assertThrows(
                    NodeModuleResolver.ModuleNotFoundException.class,
                    () ->
                            resolver.packageImportsResolve(
                                    "#/foo", "/project/index.js", NodeConditions.CJS_CONDITIONS));
        }

        @Test
        void throwsWhenNoImportsDefined() {
            fs.addFile("/project/package.json", "{\"name\": \"test\"}");
            assertThrows(
                    NodeModuleResolver.ModuleNotFoundException.class,
                    () ->
                            resolver.packageImportsResolve(
                                    "#foo", "/project/index.js", NodeConditions.CJS_CONDITIONS));
        }
    }

    @Nested
    class PackageSelfResolve {

        @Test
        void resolvesSelfReference() {
            fs.addFile(
                    "/project/package.json",
                    "{\"name\": \"my-pkg\", \"exports\": {\".\": \"./index.js\", \"./utils\": \"./lib/utils.js\"}}");
            fs.addFile("/project/lib/utils.js", "");

            ResolvedModule result =
                    resolver.packageSelfResolve("my-pkg", "./utils", "/project/src/index.js");
            assertNotNull(result);
            assertEquals("/project/lib/utils.js", result.getPath());
        }

        @Test
        void returnsNullForDifferentName() {
            fs.addFile(
                    "/project/package.json",
                    "{\"name\": \"my-pkg\", \"exports\": {\".\": \"./index.js\"}}");

            ResolvedModule result =
                    resolver.packageSelfResolve("other-pkg", ".", "/project/src/index.js");
            assertNull(result);
        }

        @Test
        void returnsNullWithoutExports() {
            fs.addFile("/project/package.json", "{\"name\": \"my-pkg\"}");

            ResolvedModule result =
                    resolver.packageSelfResolve("my-pkg", ".", "/project/src/index.js");
            assertNull(result);
        }
    }

    @Nested
    class PatternKeyCompare {

        @Test
        void longerPrefixIsMoreSpecific() {
            assertTrue(resolver.patternKeyCompare("./lib/*", "./*") < 0);
        }

        @Test
        void longerSuffixIsMoreSpecific() {
            assertTrue(resolver.patternKeyCompare("./*.js", "./*") < 0);
        }

        @Test
        void sameKeyIsEqual() {
            assertEquals(0, resolver.patternKeyCompare("./*", "./*"));
        }
    }

    @Nested
    class HashImportViaCjsResolve {

        @Test
        void cjsResolveHandlesHashImport() {
            fs.addFile(
                    "/project/package.json",
                    "{\"name\": \"test\", \"imports\": {\"#utils\": \"./src/utils.js\"}}");
            fs.addFile("/project/src/utils.js", "");

            ResolvedModule result = resolver.cjsResolve("#utils", "/project/src/index.js");
            assertEquals("/project/src/utils.js", result.getPath());
        }
    }

    @Nested
    class LoadNodeModulesWithExports {

        @Test
        void usesExportsFieldWhenPresent() {
            fs.addFile(
                    "/project/node_modules/pkg/package.json",
                    "{\"name\": \"pkg\", \"exports\": {\".\": {\"require\": \"./cjs.js\"}}}");
            fs.addFile("/project/node_modules/pkg/cjs.js", "");

            ResolvedModule result = resolver.loadNodeModules("pkg", "/project/src");
            assertNotNull(result);
            assertEquals("/project/node_modules/pkg/cjs.js", result.getPath());
        }

        @Test
        void subpathExportsInNodeModules() {
            fs.addFile(
                    "/project/node_modules/pkg/package.json",
                    "{\"name\": \"pkg\", \"exports\": {\".\": \"./main.js\", \"./sub\": \"./lib/sub.js\"}}");
            fs.addFile("/project/node_modules/pkg/lib/sub.js", "");

            ResolvedModule result = resolver.loadNodeModules("pkg/sub", "/project/src");
            assertNotNull(result);
            assertEquals("/project/node_modules/pkg/lib/sub.js", result.getPath());
        }
    }
}
