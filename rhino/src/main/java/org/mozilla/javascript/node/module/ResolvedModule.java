/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.node.module;

/** The result of resolving a module specifier. */
public class ResolvedModule {

    /** The format of the resolved module. */
    public enum ModuleFormat {
        MODULE,
        COMMONJS,
        JSON
    }

    private final String path;
    private final ModuleFormat format;

    public ResolvedModule(String path, ModuleFormat format) {
        this.path = path;
        this.format = format;
    }

    /** The absolute resolved file path. */
    public String getPath() {
        return path;
    }

    /** The module format (MODULE, COMMONJS, or JSON). */
    public ModuleFormat getFormat() {
        return format;
    }

    @Override
    public String toString() {
        return "ResolvedModule{path='" + path + "', format=" + format + "}";
    }
}
