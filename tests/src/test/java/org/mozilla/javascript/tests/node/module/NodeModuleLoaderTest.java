/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests.node.module;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.es6module.ModuleRecord;
import org.mozilla.javascript.node.module.DefaultNodeFileSystem;
import org.mozilla.javascript.node.module.NodeModuleLoader;

class NodeModuleLoaderTest {

    private static final String FIXTURES_DIR =
            Path.of("src/test/resources/node-modules-test").toAbsolutePath().toString();

    @Test
    void resolvesEsmModuleWithConditionalExports() throws Exception {
        DefaultNodeFileSystem fs = new DefaultNodeFileSystem();
        NodeModuleLoader loader = new NodeModuleLoader(fs);

        String esmProjectIndex = Path.of(FIXTURES_DIR, "esm-project", "index.mjs").toString();

        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);

            // Create a synthetic referrer module at the index.mjs path
            ModuleRecord referrer = cx.compileModule("export default 1;", esmProjectIndex, 1, null);

            // Resolve dep-a from esm-project — should pick the "import" condition
            String resolved = loader.resolveModule("dep-a", referrer);
            assertTrue(
                    resolved.endsWith("/esm/index.mjs"),
                    "Should resolve to ESM export, got: " + resolved);
        }
    }

    @Test
    void resolvesRelativeModule() throws Exception {
        DefaultNodeFileSystem fs = new DefaultNodeFileSystem();
        NodeModuleLoader loader = new NodeModuleLoader(fs);

        String projectIndex = Path.of(FIXTURES_DIR, "project", "index.js").toString();

        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);

            ModuleRecord referrer = cx.compileModule("export default 1;", projectIndex, 1, null);

            String resolved = loader.resolveModule("./local.js", referrer);
            assertTrue(
                    resolved.endsWith("/project/local.js"),
                    "Should resolve to local.js, got: " + resolved);
        }
    }

    @Test
    void loadsAndCachesModule() throws Exception {
        DefaultNodeFileSystem fs = new DefaultNodeFileSystem();
        NodeModuleLoader loader = new NodeModuleLoader(fs);

        String esmIndex =
                Path.of(FIXTURES_DIR, "esm-project", "node_modules", "dep-a", "esm", "index.mjs")
                        .toString();

        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);

            ModuleRecord record = loader.loadModule(cx, esmIndex);
            assertNotNull(record);

            // Should be cached
            ModuleRecord cached = loader.getCachedModule(esmIndex);
            assertSame(record, cached);
        }
    }

    @Test
    void cjsNamedExportsAvailableInEsm() throws Exception {
        DefaultNodeFileSystem fs = new DefaultNodeFileSystem();
        NodeModuleLoader loader = new NodeModuleLoader(fs);

        String projectDir = Path.of(FIXTURES_DIR, "cjs-named-exports").toString();
        String indexPath = Path.of(projectDir, "index.mjs").toString();

        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setOptimizationLevel(-1); // interpreter mode for CJS execution
            Scriptable scope = cx.initStandardObjects();
            loader.setGlobalScope(scope);
            cx.setModuleLoader(loader);

            ModuleRecord mainModule = loader.loadModule(cx, indexPath);
            Scriptable ns = cx.linkAndEvaluateModule(scope, mainModule);

            assertEquals("Hello, world", ns.get("greeting", ns).toString());
            assertEquals("1.0.0", ns.get("ver", ns).toString());
            Object pi = ns.get("pi", ns);
            assertTrue(pi instanceof Number, "PI should be a number");
            assertEquals(3.14159, ((Number) pi).doubleValue(), 0.00001);
        }
    }

    @Test
    void throwsForUnresolvableModule() {
        DefaultNodeFileSystem fs = new DefaultNodeFileSystem();
        NodeModuleLoader loader = new NodeModuleLoader(fs);

        String projectIndex = Path.of(FIXTURES_DIR, "project", "index.js").toString();

        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);

            ModuleRecord referrer = cx.compileModule("export default 1;", projectIndex, 1, null);

            assertThrows(
                    Exception.class, () -> loader.resolveModule("nonexistent-package", referrer));
        }
    }
}
