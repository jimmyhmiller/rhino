/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.es6module;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * Gets an export binding.
     *
     * @param exportName the export name
     * @return the exported value, or null if not found
     */
    public Object getExportBinding(String exportName) {
        return exportBindings.get(exportName);
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
