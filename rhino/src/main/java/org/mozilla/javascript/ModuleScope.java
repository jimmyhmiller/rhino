/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import org.mozilla.javascript.es6module.ModuleLoader;
import org.mozilla.javascript.es6module.ModuleRecord;

/**
 * Represents the module environment record for an ES6 module.
 *
 * <p>Each module has its own module environment which contains the bindings for all top-level
 * declarations. Import bindings are resolved to their source module's exports.
 *
 * @see <a href="https://tc39.es/ecma262/#sec-module-environment-records">Module Environment
 *     Records</a>
 */
public class ModuleScope extends TopLevel {
    private static final long serialVersionUID = 1L;

    private final ModuleRecord moduleRecord;

    /**
     * Creates a module scope.
     *
     * @param parentScope the parent scope (typically the global object)
     * @param moduleRecord the module record this scope belongs to
     */
    public ModuleScope(Scriptable parentScope, ModuleRecord moduleRecord) {
        this.moduleRecord = moduleRecord;
        setParentScope(parentScope);
        // Module code is always strict
        setPrototype(ScriptableObject.getObjectPrototype(parentScope));
    }

    /** Returns the module record associated with this scope. */
    public ModuleRecord getModuleRecord() {
        return moduleRecord;
    }

    @Override
    public String getClassName() {
        return "Module";
    }

    /**
     * Gets a property, with special handling for import bindings.
     *
     * <p>If the name is an import binding, it resolves to the source module's export.
     */
    @Override
    public Object get(String name, Scriptable start) {
        // First check if this is an import binding
        for (ModuleRecord.ImportEntry entry : moduleRecord.getImportEntries()) {
            if (entry.getLocalName().equals(name)) {
                return resolveImportBinding(entry);
            }
        }
        // Otherwise use normal property lookup
        return super.get(name, start);
    }

    /**
     * Resolves an import binding to its source module's export value.
     *
     * @param entry the import entry to resolve
     * @return the imported value
     */
    private Object resolveImportBinding(ModuleRecord.ImportEntry entry) {
        Context cx = Context.getCurrentContext();
        if (cx == null || cx.getModuleLoader() == null) {
            throw ScriptRuntime.constructError(
                    "Error", "Cannot resolve import: no module loader available");
        }

        ModuleLoader loader = cx.getModuleLoader();
        try {
            String resolved = loader.resolveModule(entry.getModuleRequest(), moduleRecord);
            ModuleRecord sourceModule = loader.getCachedModule(resolved);

            if (sourceModule == null) {
                throw ScriptRuntime.constructError(
                        "Error", "Module '" + entry.getModuleRequest() + "' not loaded");
            }

            if (entry.isNamespaceImport()) {
                // import * as ns from 'module'
                return sourceModule.getNamespaceObject();
            } else {
                // import { name } from 'module' or import name from 'module'
                String importName = entry.getImportName();
                if ("default".equals(importName)) {
                    return sourceModule.getExportBinding("default");
                }
                return sourceModule.getExportBinding(importName);
            }
        } catch (ModuleLoader.ModuleResolutionException e) {
            throw ScriptRuntime.constructError(
                    "Error", "Cannot resolve import from '" + entry.getModuleRequest() + "'");
        }
    }

    /** Checks if a binding exists, including import bindings. */
    @Override
    public boolean has(String name, Scriptable start) {
        // Check import bindings
        for (ModuleRecord.ImportEntry entry : moduleRecord.getImportEntries()) {
            if (entry.getLocalName().equals(name)) {
                return true;
            }
        }
        return super.has(name, start);
    }

    /** Sets a property. Import bindings are immutable and will throw in strict mode. */
    @Override
    public void put(String name, Scriptable start, Object value) {
        // Check if this is an import binding (immutable)
        for (ModuleRecord.ImportEntry entry : moduleRecord.getImportEntries()) {
            if (entry.getLocalName().equals(name)) {
                throw ScriptRuntime.typeErrorById("msg.modify.readonly", name);
            }
        }
        super.put(name, start, value);
    }
}
