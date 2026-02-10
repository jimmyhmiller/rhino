/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests.node.module;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.node.module.DefaultNodeFileSystem;
import org.mozilla.javascript.node.module.NodeModuleResolver;
import org.mozilla.javascript.node.module.PackageJsonReader;

class NodeRequireTest {

    private static final String FIXTURES_DIR =
            Path.of("src/test/resources/node-modules-test").toAbsolutePath().toString();

    @Test
    void resolverResolvesDepAFromProject() {
        // Test that the resolver (used by NodeRequire) can find dep-a
        DefaultNodeFileSystem fs = new DefaultNodeFileSystem();
        PackageJsonReader pkgReader = new PackageJsonReader(fs);
        NodeModuleResolver resolver = new NodeModuleResolver(fs, pkgReader);

        String parentPath = Path.of(FIXTURES_DIR, "project", "index.js").toString();
        var result = resolver.cjsResolve("dep-a", parentPath);
        assertNotNull(result);
        assertTrue(
                result.getPath().endsWith("/dep-a/lib/index.js"),
                "Should resolve to dep-a CJS via exports, got: " + result.getPath());
    }

    @Test
    void resolverResolvesSubpath() {
        DefaultNodeFileSystem fs = new DefaultNodeFileSystem();
        PackageJsonReader pkgReader = new PackageJsonReader(fs);
        NodeModuleResolver resolver = new NodeModuleResolver(fs, pkgReader);

        String parentPath = Path.of(FIXTURES_DIR, "project", "index.js").toString();
        var result = resolver.cjsResolve("dep-a/utils", parentPath);
        assertNotNull(result);
        assertTrue(
                result.getPath().endsWith("/dep-a/lib/utils.js"),
                "Should resolve subpath via exports, got: " + result.getPath());
    }

    @Test
    void resolverResolvesScopedPackage() {
        DefaultNodeFileSystem fs = new DefaultNodeFileSystem();
        PackageJsonReader pkgReader = new PackageJsonReader(fs);
        NodeModuleResolver resolver = new NodeModuleResolver(fs, pkgReader);

        String parentPath = Path.of(FIXTURES_DIR, "project", "index.js").toString();
        var result = resolver.cjsResolve("@scope/dep-b", parentPath);
        assertNotNull(result);
        assertTrue(
                result.getPath().endsWith("/dep-b/main.js"),
                "Should resolve scoped package, got: " + result.getPath());
    }

    @Test
    void resolverResolvesScopedPackageSubpath() {
        DefaultNodeFileSystem fs = new DefaultNodeFileSystem();
        PackageJsonReader pkgReader = new PackageJsonReader(fs);
        NodeModuleResolver resolver = new NodeModuleResolver(fs, pkgReader);

        String parentPath = Path.of(FIXTURES_DIR, "project", "index.js").toString();
        var result = resolver.cjsResolve("@scope/dep-b/sub", parentPath);
        assertNotNull(result);
        assertTrue(
                result.getPath().endsWith("/dep-b/sub.js"),
                "Should resolve scoped package subpath, got: " + result.getPath());
    }

    @Test
    void resolverResolvesNestedNodeModules() {
        DefaultNodeFileSystem fs = new DefaultNodeFileSystem();
        PackageJsonReader pkgReader = new PackageJsonReader(fs);
        NodeModuleResolver resolver = new NodeModuleResolver(fs, pkgReader);

        String parentPath =
                Path.of(FIXTURES_DIR, "project", "node_modules", "dep-c", "index.js").toString();
        var result = resolver.cjsResolve("dep-d", parentPath);
        assertNotNull(result);
        assertTrue(
                result.getPath().endsWith("/dep-c/node_modules/dep-d/index.js"),
                "Should resolve from nested node_modules, got: " + result.getPath());
    }

    @Test
    void resolverResolvesRelativeFile() {
        DefaultNodeFileSystem fs = new DefaultNodeFileSystem();
        PackageJsonReader pkgReader = new PackageJsonReader(fs);
        NodeModuleResolver resolver = new NodeModuleResolver(fs, pkgReader);

        String parentPath = Path.of(FIXTURES_DIR, "project", "index.js").toString();
        var result = resolver.cjsResolve("./local", parentPath);
        assertNotNull(result);
        assertTrue(
                result.getPath().endsWith("/project/local.js"),
                "Should resolve relative file, got: " + result.getPath());
    }
}
