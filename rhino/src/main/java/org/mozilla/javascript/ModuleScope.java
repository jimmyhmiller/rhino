/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.HashMap;
import java.util.Map;
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

    /** Cache of import local name -> source module, to avoid repeated module resolution I/O. */
    private transient Map<String, ModuleRecord> resolvedImports;

    /** Cache of import local name -> ImportEntry for fast lookup. */
    private transient Map<String, ModuleRecord.ImportEntry> importEntryMap;

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

    private void ensureImportEntryMap() {
        if (importEntryMap == null) {
            importEntryMap = new HashMap<>();
            for (ModuleRecord.ImportEntry entry : moduleRecord.getImportEntries()) {
                importEntryMap.put(entry.getLocalName(), entry);
            }
        }
    }

    /**
     * Gets a property, with special handling for import bindings.
     *
     * <p>If the name is an import binding, it resolves to the source module's export.
     */
    @Override
    public Object get(String name, Scriptable start) {
        ensureImportEntryMap();
        ModuleRecord.ImportEntry entry = importEntryMap.get(name);
        if (entry != null) {
            return resolveImportBinding(entry);
        }
        // Otherwise use normal property lookup
        return super.get(name, start);
    }

    /**
     * Resolves an import binding to its source module's export value.
     *
     * <p>Source modules are cached after first resolution to avoid repeated filesystem I/O. Export
     * bindings are still fetched each time to support ES module live bindings.
     *
     * @param entry the import entry to resolve
     * @return the imported value
     */
    private Object resolveImportBinding(ModuleRecord.ImportEntry entry) {
        String localName = entry.getLocalName();

        // Check cache for already-resolved source module
        if (resolvedImports != null) {
            ModuleRecord sourceModule = resolvedImports.get(localName);
            if (sourceModule != null) {
                return getBindingFromModule(entry, sourceModule);
            }
        }

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

            // Cache the resolved source module
            if (resolvedImports == null) {
                resolvedImports = new HashMap<>();
            }
            resolvedImports.put(localName, sourceModule);

            return getBindingFromModule(entry, sourceModule);
        } catch (ModuleLoader.ModuleResolutionException e) {
            throw ScriptRuntime.constructError(
                    "Error", "Cannot resolve import from '" + entry.getModuleRequest() + "'");
        }
    }

    private static Object getBindingFromModule(
            ModuleRecord.ImportEntry entry, ModuleRecord sourceModule) {
        if (entry.isNamespaceImport()) {
            return sourceModule.getNamespaceObject();
        }
        String importName = entry.getImportName();
        if ("default".equals(importName)) {
            return sourceModule.getExportBinding("default");
        }
        return sourceModule.getExportBinding(importName);
    }

    /** Checks if a binding exists, including import bindings. */
    @Override
    public boolean has(String name, Scriptable start) {
        ensureImportEntryMap();
        if (importEntryMap.containsKey(name)) {
            return true;
        }
        return super.has(name, start);
    }

    /** Sets a property. Import bindings are immutable and will throw in strict mode. */
    @Override
    public void put(String name, Scriptable start, Object value) {
        ensureImportEntryMap();
        if (importEntryMap.containsKey(name)) {
            throw ScriptRuntime.typeErrorById("msg.modify.readonly", name);
        }
        super.put(name, start, value);
    }
}
