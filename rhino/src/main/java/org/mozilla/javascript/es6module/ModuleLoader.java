/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.es6module;

import org.mozilla.javascript.Context;

/**
 * Interface for loading ES6 modules.
 *
 * <p>This interface must be implemented by the host environment to provide module resolution and
 * loading capabilities. The host is responsible for:
 *
 * <ul>
 *   <li>Resolving module specifiers to canonical identifiers
 *   <li>Loading module source code
 *   <li>Caching loaded modules
 * </ul>
 *
 * @see <a
 *     href="https://tc39.es/ecma262/#sec-hostresolveimportedmodule">HostResolveImportedModule</a>
 */
public interface ModuleLoader {

    /**
     * Resolves a module specifier to a canonical module identifier.
     *
     * <p>The resolution algorithm is host-defined. Typical implementations might:
     *
     * <ul>
     *   <li>Resolve relative paths (./foo, ../bar) relative to the referrer
     *   <li>Resolve bare specifiers (lodash) using a package map
     *   <li>Validate that the specifier is allowed
     * </ul>
     *
     * @param specifier the module specifier from the import/export statement
     * @param referrer the module that contains the import, or null for the entry module
     * @return the resolved canonical module identifier
     * @throws ModuleResolutionException if the specifier cannot be resolved
     */
    String resolveModule(String specifier, ModuleRecord referrer) throws ModuleResolutionException;

    /**
     * Loads and parses a module.
     *
     * <p>This method is called when a module needs to be loaded. The implementation should:
     *
     * <ol>
     *   <li>Fetch the module source code
     *   <li>Parse it as a module (using {@link org.mozilla.javascript.Parser#parseModule})
     *   <li>Create and return a ModuleRecord
     * </ol>
     *
     * <p>The implementation should cache modules to ensure that the same module is not loaded
     * multiple times.
     *
     * @param cx the current context
     * @param resolvedSpecifier the resolved module identifier (from resolveModule)
     * @return the loaded module record
     * @throws ModuleLoadException if the module cannot be loaded
     */
    ModuleRecord loadModule(Context cx, String resolvedSpecifier) throws ModuleLoadException;

    /**
     * Returns a cached module if available.
     *
     * @param resolvedSpecifier the resolved module identifier
     * @return the cached module, or null if not cached
     */
    ModuleRecord getCachedModule(String resolvedSpecifier);

    /** Exception thrown when module resolution fails. */
    class ModuleResolutionException extends Exception {
        private static final long serialVersionUID = 1L;

        public ModuleResolutionException(String message) {
            super(message);
        }

        public ModuleResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Exception thrown when module loading fails. */
    class ModuleLoadException extends Exception {
        private static final long serialVersionUID = 1L;

        public ModuleLoadException(String message) {
            super(message);
        }

        public ModuleLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
