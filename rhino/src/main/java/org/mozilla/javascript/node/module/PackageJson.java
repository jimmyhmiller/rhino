/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.node.module;

import java.util.Map;

/**
 * Immutable representation of a parsed package.json file. The {@code exports} and {@code imports}
 * fields are stored as raw parsed JSON structures (String, Map, or List) to support all the
 * variations allowed by the Node.js spec.
 */
public class PackageJson {

    private final String name;
    private final String main;
    private final String type;
    private final Object exports;
    private final Object imports;
    private final String directory;

    @SuppressWarnings("unchecked")
    public PackageJson(Map<String, Object> parsed, String directory) {
        this.name = stringOrNull(parsed.get("name"));
        this.main = stringOrNull(parsed.get("main"));
        String t = stringOrNull(parsed.get("type"));
        this.type = t != null ? t : "commonjs";
        this.exports = parsed.get("exports");
        this.imports = parsed.get("imports");
        this.directory = directory;
    }

    private static String stringOrNull(Object value) {
        return value instanceof String ? (String) value : null;
    }

    public String getName() {
        return name;
    }

    public String getMain() {
        return main;
    }

    /** Returns the type field, defaulting to {@code "commonjs"} when absent. */
    public String getType() {
        return type;
    }

    /**
     * Returns the raw exports field. May be a String, Map, or List depending on the package.json
     * content.
     */
    public Object getExports() {
        return exports;
    }

    /**
     * Returns the raw imports field. May be a String, Map, or List depending on the package.json
     * content.
     */
    public Object getImports() {
        return imports;
    }

    public String getDirectory() {
        return directory;
    }

    public boolean hasExports() {
        return exports != null;
    }

    public boolean hasImports() {
        return imports != null;
    }
}
