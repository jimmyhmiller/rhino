/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests.node.module;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.node.module.PackageJson;
import org.mozilla.javascript.node.module.PackageJsonReader;

class PackageJsonReaderTest {

    @Test
    void readsBasicPackageJson() {
        MockNodeFileSystem fs = new MockNodeFileSystem();
        fs.addFile(
                "/project/package.json",
                "{\"name\": \"test\", \"main\": \"./index.js\", \"type\": \"module\"}");

        PackageJsonReader reader = new PackageJsonReader(fs);
        PackageJson pkg = reader.read("/project");

        assertNotNull(pkg);
        assertEquals("test", pkg.getName());
        assertEquals("./index.js", pkg.getMain());
        assertEquals("module", pkg.getType());
        assertEquals("/project", pkg.getDirectory());
    }

    @Test
    void defaultsTypeToCommonjs() {
        MockNodeFileSystem fs = new MockNodeFileSystem();
        fs.addFile("/project/package.json", "{\"name\": \"test\"}");

        PackageJsonReader reader = new PackageJsonReader(fs);
        PackageJson pkg = reader.read("/project");

        assertNotNull(pkg);
        assertEquals("commonjs", pkg.getType());
    }

    @Test
    void returnsNullForMissingPackageJson() {
        MockNodeFileSystem fs = new MockNodeFileSystem();
        fs.addDirectory("/project");

        PackageJsonReader reader = new PackageJsonReader(fs);
        assertNull(reader.read("/project"));
    }

    @Test
    void cachesResults() {
        MockNodeFileSystem fs = new MockNodeFileSystem();
        fs.addFile("/project/package.json", "{\"name\": \"test\"}");

        PackageJsonReader reader = new PackageJsonReader(fs);
        PackageJson first = reader.read("/project");
        PackageJson second = reader.read("/project");

        assertSame(first, second);
    }

    @Test
    void cachesAbsentResults() {
        MockNodeFileSystem fs = new MockNodeFileSystem();
        fs.addDirectory("/project");

        PackageJsonReader reader = new PackageJsonReader(fs);
        assertNull(reader.read("/project"));
        // Second call should also return null (from cache)
        assertNull(reader.read("/project"));
    }

    @Test
    void findsNearestPackageJson() {
        MockNodeFileSystem fs = new MockNodeFileSystem();
        fs.addFile("/project/package.json", "{\"name\": \"root\"}");
        fs.addDirectory("/project/src/lib");

        PackageJsonReader reader = new PackageJsonReader(fs);
        PackageJson pkg = reader.findNearest("/project/src/lib");

        assertNotNull(pkg);
        assertEquals("root", pkg.getName());
    }

    @Test
    void findsNearestStopsAtClosest() {
        MockNodeFileSystem fs = new MockNodeFileSystem();
        fs.addFile("/project/package.json", "{\"name\": \"root\"}");
        fs.addFile("/project/packages/sub/package.json", "{\"name\": \"sub\"}");

        PackageJsonReader reader = new PackageJsonReader(fs);
        PackageJson pkg = reader.findNearest("/project/packages/sub/src");

        assertNotNull(pkg);
        assertEquals("sub", pkg.getName());
    }

    @Test
    void findNearestReturnsNullWhenNoneFound() {
        MockNodeFileSystem fs = new MockNodeFileSystem();
        fs.addDirectory("/project");

        PackageJsonReader reader = new PackageJsonReader(fs);
        assertNull(reader.findNearest("/project"));
    }

    @Test
    void parsesExportsField() {
        MockNodeFileSystem fs = new MockNodeFileSystem();
        fs.addFile(
                "/project/package.json",
                "{\"name\": \"test\", \"exports\": {\".\": \"./index.js\"}}");

        PackageJsonReader reader = new PackageJsonReader(fs);
        PackageJson pkg = reader.read("/project");

        assertTrue(pkg.hasExports());
        assertFalse(pkg.hasImports());
    }

    @Test
    void parsesImportsField() {
        MockNodeFileSystem fs = new MockNodeFileSystem();
        fs.addFile(
                "/project/package.json",
                "{\"name\": \"test\", \"imports\": {\"#internal\": \"./src/internal.js\"}}");

        PackageJsonReader reader = new PackageJsonReader(fs);
        PackageJson pkg = reader.read("/project");

        assertFalse(pkg.hasExports());
        assertTrue(pkg.hasImports());
    }
}
