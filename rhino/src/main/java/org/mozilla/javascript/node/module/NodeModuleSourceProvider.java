/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.node.module;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.commonjs.module.provider.ModuleSource;
import org.mozilla.javascript.commonjs.module.provider.ModuleSourceProvider;

/**
 * A {@link ModuleSourceProvider} that uses {@link NodeModuleResolver} for CJS module resolution.
 * Handles .json files by wrapping content as {@code module.exports = <json>;}.
 */
public class NodeModuleSourceProvider implements ModuleSourceProvider {

    private final NodeModuleResolver resolver;
    private final NodeFileSystem fs;

    public NodeModuleSourceProvider(NodeModuleResolver resolver, NodeFileSystem fs) {
        this.resolver = resolver;
        this.fs = fs;
    }

    @Override
    public ModuleSource loadSource(String moduleId, Scriptable paths, Object validator)
            throws IOException, URISyntaxException {
        // Ensure the path is absolute — requireMain may pass a relative filename
        String absPath = fs.getAbsolutePath(moduleId);
        return loadFromPath(absPath);
    }

    @Override
    public ModuleSource loadSource(URI uri, URI baseUri, Object validator)
            throws IOException, URISyntaxException {
        String path = uri.getPath();
        if (path == null) {
            path = uri.toString();
        }
        return loadFromPath(fs.getAbsolutePath(path));
    }

    private ModuleSource loadFromPath(String path) throws IOException, URISyntaxException {
        if (!fs.isFile(path)) {
            return null;
        }

        String source = fs.readFile(path);
        URI uri = new java.io.File(path).toURI();

        if (path.endsWith(".json")) {
            source = "module.exports = " + source + ";";
        }

        return new ModuleSource(new StringReader(source), null, uri, null, null);
    }
}
