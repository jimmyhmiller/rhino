/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.es6module;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

/**
 * Represents an ES6 Module Record per the ECMAScript specification.
 *
 * <p>A Module Record encapsulates structural information about the imports and exports of a single
 * module, as well as its current loading/linking/evaluation status.
 *
 * @see <a href="https://tc39.es/ecma262/#sec-abstract-module-records">ECMAScript Module Records</a>
 */
public class ModuleRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Module status values per the ECMAScript specification. */
    public enum Status {
        /** Module has been parsed but not yet linked. */
        UNLINKED,
        /** Module is currently being linked (cycle detection). */
        LINKING,
        /** Module has been successfully linked. */
        LINKED,
        /** Module is currently being evaluated (cycle detection). */
        EVALUATING,
        /** Module has been successfully evaluated. */
        EVALUATED,
        /** Module evaluation resulted in an error. */
        EVALUATED_ERROR
    }

    private final String specifier;
    private Script script;
    private Status status = Status.UNLINKED;
    private Throwable evaluationError;

    // Module namespace object (created on first access)
    private Scriptable namespaceObject;

    // Export bindings: exportName -> value
    private final Map<String, Object> exportBindings = new HashMap<>();

    // Requested modules (import specifiers)
    private final List<String> requestedModules = new ArrayList<>();

    // Import entries (for linking)
    private final List<ImportEntry> importEntries = new ArrayList<>();

    // Export entries (for linking)
    private final List<ExportEntry> localExportEntries = new ArrayList<>();
    private final List<ExportEntry> indirectExportEntries = new ArrayList<>();
    private final List<ExportEntry> starExportEntries = new ArrayList<>();

    // Environment record (scope) for this module
    private Scriptable moduleEnvironment;

    /**
     * Creates a new module record.
     *
     * @param specifier the module specifier (e.g., "./module.js")
     */
    public ModuleRecord(String specifier) {
        this.specifier = specifier;
    }

    /** Returns the module specifier. */
    public String getSpecifier() {
        return specifier;
    }

    /** Returns the compiled script for this module. */
    public Script getScript() {
        return script;
    }

    /** Sets the compiled script for this module. */
    public void setScript(Script script) {
        this.script = script;
    }

    /** Returns the current status of this module. */
    public Status getStatus() {
        return status;
    }

    /** Sets the status of this module. */
    public void setStatus(Status status) {
        this.status = status;
    }

    /** Returns the evaluation error if status is EVALUATED_ERROR. */
    public Throwable getEvaluationError() {
        return evaluationError;
    }

    /** Sets the evaluation error. */
    public void setEvaluationError(Throwable error) {
        this.evaluationError = error;
        this.status = Status.EVALUATED_ERROR;
    }

    /** Returns the namespace object for this module. */
    public Scriptable getNamespaceObject() {
        return namespaceObject;
    }

    /** Sets the namespace object for this module. */
    public void setNamespaceObject(Scriptable namespaceObject) {
        this.namespaceObject = namespaceObject;
    }

    /** Returns the module environment (scope). */
    public Scriptable getModuleEnvironment() {
        return moduleEnvironment;
    }

    /** Sets the module environment (scope). */
    public void setModuleEnvironment(Scriptable moduleEnvironment) {
        this.moduleEnvironment = moduleEnvironment;
    }

    /** Returns the export bindings map. */
    public Map<String, Object> getExportBindings() {
        return exportBindings;
    }

    /**
     * Sets an export binding.
     *
     * @param exportName the export name
     * @param value the exported value
     */
    public void setExportBinding(String exportName, Object value) {
        exportBindings.put(exportName, value);
    }

    /**
     * Gets an export binding value. This is a convenience wrapper around resolveExport that
     * retrieves the actual value from the resolved binding.
     *
     * @param exportName the export name
     * @return the exported value
     * @throws RuntimeException if the binding is not found, ambiguous, or in the TDZ
     */
    public Object getExportBinding(String exportName) {
        ResolvedBinding resolution = resolveExport(exportName, new HashSet<>());
        if (resolution == null) {
            // Not found - check cached bindings as fallback
            Object cached = exportBindings.get(exportName);
            if (cached != null) {
                return cached;
            }
            throw org.mozilla.javascript.ScriptRuntime.constructError(
                    "SyntaxError", "Export '" + exportName + "' is not defined");
        }
        if (resolution == ResolvedBinding.AMBIGUOUS) {
            throw org.mozilla.javascript.ScriptRuntime.constructError(
                    "SyntaxError", "Export '" + exportName + "' is ambiguous");
        }
        return resolution.getBindingValue();
    }

    /**
     * Resolves an export name to its source module and binding name, following the ES6 spec
     * ResolveExport algorithm (ECMA-262 16.2.1.7.2.2).
     *
     * <p>This implements cycle detection via resolveSet, handles local exports, indirect exports
     * (re-exports), and star exports with ambiguity detection.
     *
     * @param exportName the export name to resolve
     * @param resolveSet set of (module, exportName) pairs for cycle detection
     * @return the resolved binding, null if not found, or AMBIGUOUS if ambiguous
     */
    public ResolvedBinding resolveExport(String exportName, Set<String> resolveSet) {
        // 1. Cycle detection: if we've already visited this (module, exportName) pair, return null
        String resolveKey = System.identityHashCode(this) + ":" + exportName;
        if (resolveSet.contains(resolveKey)) {
            // Circular reference - return null per spec
            return null;
        }
        resolveSet.add(resolveKey);

        // 2. Check local exports first
        for (ExportEntry entry : localExportEntries) {
            if (exportName.equals(entry.getExportName())) {
                // Found a local export - return binding to this module
                return new ResolvedBinding(this, entry.getLocalName());
            }
        }

        // 3. Check indirect exports (re-exports like: export { x } from 'module')
        for (ExportEntry entry : indirectExportEntries) {
            if (exportName.equals(entry.getExportName())) {
                String moduleRequest = entry.getModuleRequest();
                String importName = entry.getImportName();
                if (moduleRequest != null && importName != null) {
                    ModuleRecord sourceModule = getRequiredModule(moduleRequest);
                    if (sourceModule != null) {
                        // Handle namespace re-export: export * as ns from 'module'
                        if ("*".equals(importName)) {
                            return new ResolvedBinding(sourceModule, null, true);
                        }
                        // Recursively resolve from the source module
                        return sourceModule.resolveExport(importName, resolveSet);
                    }
                }
                return null;
            }
        }

        // 4. Check star exports (export * from 'module')
        ResolvedBinding starResolution = null;
        for (ExportEntry entry : starExportEntries) {
            String moduleRequest = entry.getModuleRequest();
            if (moduleRequest != null) {
                ModuleRecord sourceModule = getRequiredModule(moduleRequest);
                if (sourceModule != null) {
                    ResolvedBinding resolution = sourceModule.resolveExport(exportName, resolveSet);
                    if (resolution == ResolvedBinding.AMBIGUOUS) {
                        return ResolvedBinding.AMBIGUOUS;
                    }
                    if (resolution != null) {
                        if (starResolution == null) {
                            starResolution = resolution;
                        } else if (!starResolution.equals(resolution)) {
                            // Ambiguous: found in multiple star exports with different bindings
                            return ResolvedBinding.AMBIGUOUS;
                        }
                    }
                }
            }
        }

        return starResolution;
    }

    /**
     * Gets a required module from the module loader.
     *
     * @param moduleRequest the module specifier
     * @return the module record, or null if not found
     */
    private ModuleRecord getRequiredModule(String moduleRequest) {
        org.mozilla.javascript.Context cx = org.mozilla.javascript.Context.getCurrentContext();
        if (cx == null || cx.getModuleLoader() == null) {
            return null;
        }
        ModuleLoader loader = cx.getModuleLoader();
        try {
            String resolved = loader.resolveModule(moduleRequest, this);
            return loader.getCachedModule(resolved);
        } catch (ModuleLoader.ModuleResolutionException e) {
            return null;
        }
    }

    /**
     * Represents a resolved export binding per the ES6 spec. A binding points to a specific module
     * and binding name within that module's environment.
     */
    public static class ResolvedBinding {
        /** Sentinel value indicating an ambiguous export resolution. */
        public static final ResolvedBinding AMBIGUOUS = new ResolvedBinding(null, null);

        private final ModuleRecord module;
        private final String bindingName;
        private final boolean isNamespace;

        public ResolvedBinding(ModuleRecord module, String bindingName) {
            this(module, bindingName, false);
        }

        public ResolvedBinding(ModuleRecord module, String bindingName, boolean isNamespace) {
            this.module = module;
            this.bindingName = bindingName;
            this.isNamespace = isNamespace;
        }

        public ModuleRecord getModule() {
            return module;
        }

        public String getBindingName() {
            return bindingName;
        }

        public boolean isNamespace() {
            return isNamespace;
        }

        /**
         * Gets the actual value of this binding from the source module's environment.
         *
         * @return the binding value
         */
        public Object getBindingValue() {
            if (this == AMBIGUOUS || module == null) {
                return null;
            }

            // Namespace export: return the module's namespace object
            if (isNamespace) {
                return module.getNamespaceObject();
            }

            // Special case: *default* is the script result for default expression exports
            if ("*default*".equals(bindingName)) {
                return module.exportBindings.get("default");
            }

            // Live binding: look up from module scope
            Scriptable env = module.getModuleEnvironment();
            if (env != null && bindingName != null) {
                Object value =
                        org.mozilla.javascript.ScriptableObject.getProperty(env, bindingName);
                if (value == org.mozilla.javascript.Scriptable.NOT_FOUND) {
                    // Binding doesn't exist yet - this is a TDZ error
                    throw org.mozilla.javascript.ScriptRuntime.constructError(
                            "ReferenceError",
                            "Cannot access '" + bindingName + "' before initialization");
                }
                // Check for TDZ - Rhino uses a special TDZ sentinel value for let/const
                org.mozilla.javascript.Undefined.checkTDZ(value, bindingName);
                return value;
            }

            // Fall back to cached value
            return module.exportBindings.get(bindingName);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ResolvedBinding)) return false;
            ResolvedBinding other = (ResolvedBinding) obj;
            return this.module == other.module
                    && java.util.Objects.equals(this.bindingName, other.bindingName);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(System.identityHashCode(module), bindingName);
        }
    }

    /** Returns the list of requested module specifiers. */
    public List<String> getRequestedModules() {
        return requestedModules;
    }

    /** Adds a requested module specifier. */
    public void addRequestedModule(String specifier) {
        if (!requestedModules.contains(specifier)) {
            requestedModules.add(specifier);
        }
    }

    /** Returns the list of import entries. */
    public List<ImportEntry> getImportEntries() {
        return importEntries;
    }

    /** Adds an import entry. */
    public void addImportEntry(ImportEntry entry) {
        importEntries.add(entry);
    }

    /** Returns the list of local export entries. */
    public List<ExportEntry> getLocalExportEntries() {
        return localExportEntries;
    }

    /** Adds a local export entry. */
    public void addLocalExportEntry(ExportEntry entry) {
        localExportEntries.add(entry);
    }

    /** Returns the list of indirect export entries. */
    public List<ExportEntry> getIndirectExportEntries() {
        return indirectExportEntries;
    }

    /** Adds an indirect export entry. */
    public void addIndirectExportEntry(ExportEntry entry) {
        indirectExportEntries.add(entry);
    }

    /** Returns the list of star export entries. */
    public List<ExportEntry> getStarExportEntries() {
        return starExportEntries;
    }

    /** Adds a star export entry. */
    public void addStarExportEntry(ExportEntry entry) {
        starExportEntries.add(entry);
    }

    /** Represents an ImportEntry record per the ECMAScript specification. */
    public static class ImportEntry implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String moduleRequest;
        private final String importName; // null for namespace imports
        private final String localName;

        /**
         * Creates an import entry.
         *
         * @param moduleRequest the module specifier
         * @param importName the import name (or null for namespace import)
         * @param localName the local binding name
         */
        public ImportEntry(String moduleRequest, String importName, String localName) {
            this.moduleRequest = moduleRequest;
            this.importName = importName;
            this.localName = localName;
        }

        public String getModuleRequest() {
            return moduleRequest;
        }

        public String getImportName() {
            return importName;
        }

        public String getLocalName() {
            return localName;
        }

        /** Returns true if this is a namespace import (import * as name). */
        public boolean isNamespaceImport() {
            return importName == null;
        }
    }

    /** Represents an ExportEntry record per the ECMAScript specification. */
    public static class ExportEntry implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String exportName; // null for star exports without alias
        private final String moduleRequest; // null for local exports
        private final String importName; // null for local exports
        private final String localName; // null for re-exports

        /**
         * Creates an export entry.
         *
         * @param exportName the export name (null for star exports)
         * @param moduleRequest the module specifier (null for local exports)
         * @param importName the import name for re-exports (null for local exports)
         * @param localName the local binding name (null for re-exports)
         */
        public ExportEntry(
                String exportName, String moduleRequest, String importName, String localName) {
            this.exportName = exportName;
            this.moduleRequest = moduleRequest;
            this.importName = importName;
            this.localName = localName;
        }

        public String getExportName() {
            return exportName;
        }

        public String getModuleRequest() {
            return moduleRequest;
        }

        public String getImportName() {
            return importName;
        }

        public String getLocalName() {
            return localName;
        }

        /** Returns true if this is a local export. */
        public boolean isLocalExport() {
            return moduleRequest == null;
        }

        /** Returns true if this is a star export. */
        public boolean isStarExport() {
            return importName != null && importName.equals("*") && exportName == null;
        }
    }
}
