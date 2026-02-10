/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.node.module;

import java.io.IOException;

/** Filesystem abstraction for Node.js module resolution. */
public interface NodeFileSystem {

    boolean isFile(String path);

    boolean isDirectory(String path);

    String readFile(String path) throws IOException;

    String realPath(String path) throws IOException;

    String[] listDirectory(String path) throws IOException;

    String separator();

    /** Join base and child path components. */
    String resolve(String base, String child);

    /** Return the parent directory of the given path. */
    String dirname(String path);

    /** Return the absolute form of the given path. */
    String getAbsolutePath(String path);
}
