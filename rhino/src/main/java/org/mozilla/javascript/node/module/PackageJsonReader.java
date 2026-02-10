/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.node.module;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reads and caches package.json files using a {@link NodeFileSystem}. Thread-safe. */
public class PackageJsonReader {

    private static final Object ABSENT = new Object();

    private final NodeFileSystem fs;
    private final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

    public PackageJsonReader(NodeFileSystem fs) {
        this.fs = fs;
    }

    /** Read and parse the package.json in the given directory, or return null if not found. */
    @SuppressWarnings("unchecked")
    public PackageJson read(String directory) {
        Object cached = cache.get(directory);
        if (cached != null) {
            return cached == ABSENT ? null : (PackageJson) cached;
        }

        String pkgPath = fs.resolve(directory, "package.json");
        if (!fs.isFile(pkgPath)) {
            cache.put(directory, ABSENT);
            return null;
        }

        try {
            String content = fs.readFile(pkgPath);
            Object parsed = SimpleJsonParser.parse(content);
            if (!(parsed instanceof Map)) {
                cache.put(directory, ABSENT);
                return null;
            }
            PackageJson pkg = new PackageJson((Map<String, Object>) parsed, directory);
            cache.put(directory, pkg);
            return pkg;
        } catch (IOException | IllegalArgumentException e) {
            cache.put(directory, ABSENT);
            return null;
        }
    }

    /**
     * Walk up the directory tree from startDir to find the nearest directory containing a
     * package.json. Returns null if none found.
     */
    public PackageJson findNearest(String startDir) {
        String dir = fs.getAbsolutePath(startDir);
        while (true) {
            PackageJson pkg = read(dir);
            if (pkg != null) {
                return pkg;
            }
            String parent = fs.dirname(dir);
            if (parent.equals(dir)) {
                return null;
            }
            dir = parent;
        }
    }
}
