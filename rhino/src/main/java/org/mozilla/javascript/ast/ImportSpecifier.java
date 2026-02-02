/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

/**
 * AST node for an import specifier within an import declaration.
 *
 * <p>Node type is {@link Token#IMPORT}.
 *
 * <pre><i>ImportSpecifier</i> :
 *       ImportedBinding
 *       IdentifierName <b>as</b> ImportedBinding
 * <i>ImportedBinding</i> :
 *       BindingIdentifier</pre>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code foo} - imports binding "foo" as local name "foo"
 *   <li>{@code foo as bar} - imports binding "foo" as local name "bar"
 * </ul>
 */
public class ImportSpecifier extends AstNode {

    private Name importedName; // name in source module (can be string literal for non-identifier)
    private Name localName; // local binding name

    {
        type = Token.IMPORT;
    }

    public ImportSpecifier() {}

    public ImportSpecifier(int pos) {
        super(pos);
    }

    public ImportSpecifier(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns the imported name (the name as it appears in the source module).
     *
     * @return the imported name
     */
    public Name getImportedName() {
        return importedName;
    }

    /**
     * Sets the imported name.
     *
     * @param importedName the imported name
     */
    public void setImportedName(Name importedName) {
        this.importedName = importedName;
        if (importedName != null) {
            importedName.setParent(this);
        }
    }

    /**
     * Returns the local binding name (the name used locally in the importing module).
     *
     * @return the local name
     */
    public Name getLocalName() {
        return localName;
    }

    /**
     * Sets the local binding name.
     *
     * @param localName the local name
     */
    public void setLocalName(Name localName) {
        this.localName = localName;
        if (localName != null) {
            localName.setParent(this);
        }
    }

    /**
     * Returns the local binding name as a string.
     *
     * @return the local name identifier, or null if not set
     */
    public String getLocalNameString() {
        return localName != null ? localName.getIdentifier() : null;
    }

    /**
     * Returns the imported name as a string.
     *
     * @return the imported name identifier, or null if not set
     */
    public String getImportedNameString() {
        return importedName != null ? importedName.getIdentifier() : null;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        if (importedName != null) {
            sb.append(importedName.toSource(0));
            if (localName != null
                    && !importedName.getIdentifier().equals(localName.getIdentifier())) {
                sb.append(" as ");
                sb.append(localName.toSource(0));
            }
        }
        return sb.toString();
    }

    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            if (importedName != null) {
                importedName.visit(v);
            }
            if (localName != null) {
                localName.visit(v);
            }
        }
    }
}
