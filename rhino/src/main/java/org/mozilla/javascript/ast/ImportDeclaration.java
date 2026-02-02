/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.mozilla.javascript.Token;

/**
 * AST node for an ES6 import declaration.
 *
 * <p>Node type is {@link Token#IMPORT}.
 *
 * <pre><i>ImportDeclaration</i> :
 *       <b>import</b> ImportClause FromClause ;
 *       <b>import</b> ModuleSpecifier ;
 * <i>ImportClause</i> :
 *       ImportedDefaultBinding
 *       NameSpaceImport
 *       NamedImports
 *       ImportedDefaultBinding , NameSpaceImport
 *       ImportedDefaultBinding , NamedImports
 * <i>ImportedDefaultBinding</i> :
 *       ImportedBinding
 * <i>NameSpaceImport</i> :
 *       <b>*</b> <b>as</b> ImportedBinding
 * <i>NamedImports</i> :
 *       <b>{</b> <b>}</b>
 *       <b>{</b> ImportsList <b>}</b>
 *       <b>{</b> ImportsList <b>,</b> <b>}</b>
 * <i>FromClause</i> :
 *       <b>from</b> ModuleSpecifier
 * <i>ModuleSpecifier</i> :
 *       StringLiteral</pre>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code import "module"} - side-effect only import
 *   <li>{@code import x from "module"} - default import
 *   <li>{@code import * as ns from "module"} - namespace import
 *   <li>{@code import { a, b as c } from "module"} - named imports
 *   <li>{@code import x, { a, b } from "module"} - default + named
 *   <li>{@code import x, * as ns from "module"} - default + namespace
 * </ul>
 */
public class ImportDeclaration extends AstNode {

    private static final List<ImportSpecifier> NO_SPECIFIERS =
            Collections.unmodifiableList(new ArrayList<>());

    private String moduleSpecifier; // "module-name"
    private Name defaultImport; // import X from ...
    private Name namespaceImport; // import * as X from ...
    private List<ImportSpecifier> namedImports; // import { a, b as c } from ...

    {
        type = Token.IMPORT;
    }

    public ImportDeclaration() {}

    public ImportDeclaration(int pos) {
        super(pos);
    }

    public ImportDeclaration(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns the module specifier string (the module path/name).
     *
     * @return the module specifier
     */
    public String getModuleSpecifier() {
        return moduleSpecifier;
    }

    /**
     * Sets the module specifier.
     *
     * @param moduleSpecifier the module specifier string
     */
    public void setModuleSpecifier(String moduleSpecifier) {
        this.moduleSpecifier = moduleSpecifier;
    }

    /**
     * Returns the default import binding name, or null if no default import.
     *
     * @return the default import name
     */
    public Name getDefaultImport() {
        return defaultImport;
    }

    /**
     * Sets the default import binding name.
     *
     * @param defaultImport the default import name
     */
    public void setDefaultImport(Name defaultImport) {
        this.defaultImport = defaultImport;
        if (defaultImport != null) {
            defaultImport.setParent(this);
        }
    }

    /**
     * Returns the namespace import binding name (from {@code * as name}), or null.
     *
     * @return the namespace import name
     */
    public Name getNamespaceImport() {
        return namespaceImport;
    }

    /**
     * Sets the namespace import binding name.
     *
     * @param namespaceImport the namespace import name
     */
    public void setNamespaceImport(Name namespaceImport) {
        this.namespaceImport = namespaceImport;
        if (namespaceImport != null) {
            namespaceImport.setParent(this);
        }
    }

    /**
     * Returns the list of named imports. Returns an immutable empty list if there are no named
     * imports.
     *
     * @return the named imports
     */
    public List<ImportSpecifier> getNamedImports() {
        return namedImports != null ? namedImports : NO_SPECIFIERS;
    }

    /**
     * Sets the list of named imports.
     *
     * @param namedImports the named imports
     */
    public void setNamedImports(List<ImportSpecifier> namedImports) {
        if (namedImports == null) {
            this.namedImports = null;
        } else {
            if (this.namedImports != null) this.namedImports.clear();
            for (ImportSpecifier spec : namedImports) addNamedImport(spec);
        }
    }

    /**
     * Adds a named import specifier.
     *
     * @param specifier the import specifier to add
     */
    public void addNamedImport(ImportSpecifier specifier) {
        assertNotNull(specifier);
        if (namedImports == null) {
            namedImports = new ArrayList<>();
        }
        namedImports.add(specifier);
        specifier.setParent(this);
    }

    /**
     * Returns true if this is a side-effect only import (no bindings).
     *
     * @return true for {@code import "module"}
     */
    public boolean isSideEffectOnly() {
        return defaultImport == null && namespaceImport == null && getNamedImports().isEmpty();
    }

    /**
     * Returns the default import name as a string.
     *
     * @return the default import identifier, or null
     */
    public String getDefaultImportString() {
        return defaultImport != null ? defaultImport.getIdentifier() : null;
    }

    /**
     * Returns the namespace import name as a string.
     *
     * @return the namespace import identifier, or null
     */
    public String getNamespaceImportString() {
        return namespaceImport != null ? namespaceImport.getIdentifier() : null;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append("import ");

        boolean hasBindings = false;

        if (defaultImport != null) {
            sb.append(defaultImport.toSource(0));
            hasBindings = true;
        }

        if (namespaceImport != null) {
            if (hasBindings) {
                sb.append(", ");
            }
            sb.append("* as ");
            sb.append(namespaceImport.toSource(0));
            hasBindings = true;
        }

        if (!getNamedImports().isEmpty()) {
            if (hasBindings) {
                sb.append(", ");
            }
            sb.append("{ ");
            boolean first = true;
            for (ImportSpecifier spec : getNamedImports()) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(spec.toSource(0));
                first = false;
            }
            sb.append(" }");
            hasBindings = true;
        }

        if (hasBindings) {
            sb.append(" from ");
        }

        sb.append("\"");
        sb.append(moduleSpecifier);
        sb.append("\";");

        return sb.toString();
    }

    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            if (defaultImport != null) {
                defaultImport.visit(v);
            }
            if (namespaceImport != null) {
                namespaceImport.visit(v);
            }
            for (ImportSpecifier spec : getNamedImports()) {
                spec.visit(v);
            }
        }
    }
}
