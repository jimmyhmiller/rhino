/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.es6module.ModuleLoader;
import org.mozilla.javascript.es6module.ModuleRecord;

/**
 * A ModuleLoader implementation for test262 module tests.
 *
 * <p>This loader resolves module specifiers relative to the test file's directory and loads modules
 * from the filesystem. It caches loaded modules to ensure each module is only loaded once.
 */
public class Test262ModuleLoader implements ModuleLoader {

    private final File baseDir;
    private final Map<String, ModuleRecord> moduleCache = new HashMap<>();

    /**
     * Creates a module loader with the given base directory.
     *
     * @param baseDir the directory to resolve relative module specifiers against
     */
    public Test262ModuleLoader(File baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public String resolveModule(String specifier, ModuleRecord referrer)
            throws ModuleResolutionException {
        try {
            File resolved;
            if (specifier.startsWith("./") || specifier.startsWith("../")) {
                // Relative path - resolve relative to referrer or base directory
                File referrerDir;
                if (referrer != null) {
                    File referrerFile = new File(referrer.getSpecifier());
                    referrerDir = referrerFile.getParentFile();
                    if (referrerDir == null) {
                        referrerDir = baseDir;
                    }
                } else {
                    referrerDir = baseDir;
                }
                resolved = new File(referrerDir, specifier);
            } else if (specifier.startsWith("/")) {
                // Absolute path
                resolved = new File(specifier);
            } else {
                // Bare specifier - resolve relative to base directory
                resolved = new File(baseDir, specifier);
            }

            // Normalize the path
            String canonicalPath = resolved.getCanonicalPath();

            // Check if file exists
            if (!new File(canonicalPath).exists()) {
                throw new ModuleResolutionException(
                        "Module not found: " + specifier + " (resolved to: " + canonicalPath + ")");
            }

            return canonicalPath;
        } catch (IOException e) {
            throw new ModuleResolutionException("Failed to resolve module: " + specifier, e);
        }
    }

    @Override
    public ModuleRecord loadModule(Context cx, String resolvedSpecifier)
            throws ModuleLoadException {
        // Check cache first
        ModuleRecord cached = moduleCache.get(resolvedSpecifier);
        if (cached != null) {
            return cached;
        }

        try {
            // Read module source
            Path path = Path.of(resolvedSpecifier);
            String source = Files.readString(path);

            // Compile the module
            ModuleRecord moduleRecord = cx.compileModule(source, resolvedSpecifier, 1, null);

            // Cache it
            moduleCache.put(resolvedSpecifier, moduleRecord);

            return moduleRecord;
        } catch (IOException e) {
            throw new ModuleLoadException("Failed to load module: " + resolvedSpecifier, e);
        }
    }

    @Override
    public ModuleRecord getCachedModule(String resolvedSpecifier) {
        return moduleCache.get(resolvedSpecifier);
    }

    /** Clears the module cache. Call this between test runs. */
    public void clearCache() {
        moduleCache.clear();
    }

    /**
     * Adds a module to the cache. This is useful for modules that are compiled externally (like the
     * main test module) so they can be found when imported by other modules or self-referential
     * imports.
     *
     * @param specifier the resolved module specifier (canonical path)
     * @param moduleRecord the module to cache
     */
    public void cacheModule(String specifier, ModuleRecord moduleRecord) {
        moduleCache.put(specifier, moduleRecord);
    }
}
