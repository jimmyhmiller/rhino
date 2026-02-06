/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tools.shell;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.es6module.ModuleLoader;
import org.mozilla.javascript.es6module.ModuleRecord;

/**
 * File-system based ES6 module loader for the Rhino shell.
 *
 * <p>Resolves module specifiers as file paths relative to the referrer module's directory. Caches
 * compiled modules to avoid re-parsing.
 */
public class FileModuleLoader implements ModuleLoader {
    final Map<String, ModuleRecord> cache = new HashMap<>();
    private final Charset charset;

    public FileModuleLoader() {
        this(StandardCharsets.UTF_8);
    }

    public FileModuleLoader(Charset charset) {
        this.charset = charset;
    }

    @Override
    public String resolveModule(String specifier, ModuleRecord referrer)
            throws ModuleResolutionException {
        try {
            Path resolved;
            if (referrer != null && referrer.getSpecifier() != null) {
                // Resolve relative to referrer's directory
                Path referrerPath = Path.of(referrer.getSpecifier());
                Path referrerDir = referrerPath.getParent();
                if (referrerDir == null) {
                    referrerDir = Path.of(".");
                }
                resolved = referrerDir.resolve(specifier).normalize().toAbsolutePath();
            } else {
                resolved = Path.of(specifier).normalize().toAbsolutePath();
            }
            return resolved.toString();
        } catch (Exception e) {
            throw new ModuleResolutionException(
                    "Cannot resolve module '" + specifier + "': " + e.getMessage(), e);
        }
    }

    @Override
    public ModuleRecord loadModule(Context cx, String resolvedSpecifier)
            throws ModuleLoadException {
        ModuleRecord cached = cache.get(resolvedSpecifier);
        if (cached != null) {
            return cached;
        }

        try {
            String source = Files.readString(Path.of(resolvedSpecifier), charset);
            ModuleRecord record = cx.compileModule(source, resolvedSpecifier, 1, null);
            cache.put(resolvedSpecifier, record);
            return record;
        } catch (IOException e) {
            throw new ModuleLoadException(
                    "Cannot load module '" + resolvedSpecifier + "': " + e.getMessage(), e);
        }
    }

    @Override
    public ModuleRecord getCachedModule(String resolvedSpecifier) {
        return cache.get(resolvedSpecifier);
    }
}
