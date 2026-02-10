/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests.node.module;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mozilla.javascript.node.module.NodeFileSystem;

/** In-memory filesystem for testing the Node.js module resolution algorithm. */
class MockNodeFileSystem implements NodeFileSystem {

    private final Map<String, String> files = new HashMap<>();
    private final Set<String> directories = new HashSet<>();

    MockNodeFileSystem() {
        directories.add("/");
    }

    MockNodeFileSystem addFile(String path, String content) {
        files.put(normalize(path), content);
        // Ensure parent directories exist
        String dir = dirname(path);
        while (!dir.equals("/") && !directories.contains(dir)) {
            directories.add(dir);
            dir = dirname(dir);
        }
        return this;
    }

    MockNodeFileSystem addDirectory(String path) {
        String norm = normalize(path);
        directories.add(norm);
        String dir = dirname(norm);
        while (!dir.equals("/") && !directories.contains(dir)) {
            directories.add(dir);
            dir = dirname(dir);
        }
        return this;
    }

    @Override
    public boolean isFile(String path) {
        return files.containsKey(normalize(path));
    }

    @Override
    public boolean isDirectory(String path) {
        return directories.contains(normalize(path));
    }

    @Override
    public String readFile(String path) throws IOException {
        String norm = normalize(path);
        String content = files.get(norm);
        if (content == null) {
            throw new IOException("File not found: " + norm);
        }
        return content;
    }

    @Override
    public String realPath(String path) {
        return normalize(path);
    }

    @Override
    public String[] listDirectory(String path) throws IOException {
        String norm = normalize(path);
        if (!directories.contains(norm)) {
            throw new IOException("Directory not found: " + norm);
        }
        String prefix = norm.endsWith("/") ? norm : norm + "/";
        List<String> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String filePath : files.keySet()) {
            if (filePath.startsWith(prefix)) {
                String rest = filePath.substring(prefix.length());
                int slash = rest.indexOf('/');
                String entry = slash == -1 ? rest : rest.substring(0, slash);
                if (!entry.isEmpty() && seen.add(entry)) {
                    entries.add(entry);
                }
            }
        }
        for (String dirPath : directories) {
            if (dirPath.startsWith(prefix)) {
                String rest = dirPath.substring(prefix.length());
                int slash = rest.indexOf('/');
                String entry = slash == -1 ? rest : rest.substring(0, slash);
                if (!entry.isEmpty() && seen.add(entry)) {
                    entries.add(entry);
                }
            }
        }
        return entries.toArray(new String[0]);
    }

    @Override
    public String separator() {
        return "/";
    }

    @Override
    public String resolve(String base, String child) {
        if (child.startsWith("/")) {
            return normalize(child);
        }
        String combined = base.endsWith("/") ? base + child : base + "/" + child;
        return normalize(combined);
    }

    @Override
    public String dirname(String path) {
        String norm = normalize(path);
        int idx = norm.lastIndexOf('/');
        if (idx <= 0) {
            return "/";
        }
        return norm.substring(0, idx);
    }

    @Override
    public String getAbsolutePath(String path) {
        return normalize(path);
    }

    /** Normalize a path by resolving . and .. components. */
    private String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String[] parts = path.split("/");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!result.isEmpty()) {
                    result.remove(result.size() - 1);
                }
            } else {
                result.add(part);
            }
        }
        if (result.isEmpty()) {
            return "/";
        }
        return "/" + String.join("/", result);
    }
}
