/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

/**
 * AST node for an export specifier within an export declaration.
 *
 * <p>Node type is {@link Token#EXPORT}.
 *
 * <pre><i>ExportSpecifier</i> :
 *       IdentifierName
 *       IdentifierName <b>as</b> IdentifierName</pre>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code foo} - exports local "foo" as "foo"
 *   <li>{@code foo as bar} - exports local "foo" as "bar"
 * </ul>
 */
public class ExportSpecifier extends AstNode {

    private Name localName; // name in local module
    private Name exportedName; // exported name (visible to importers)

    {
        type = Token.EXPORT;
    }

    public ExportSpecifier() {}

    public ExportSpecifier(int pos) {
        super(pos);
    }

    public ExportSpecifier(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns the local name (the name as it appears in the local module).
     *
     * @return the local name
     */
    public Name getLocalName() {
        return localName;
    }

    /**
     * Sets the local name.
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
     * Returns the exported name (the name visible to importing modules).
     *
     * @return the exported name
     */
    public Name getExportedName() {
        return exportedName;
    }

    /**
     * Sets the exported name.
     *
     * @param exportedName the exported name
     */
    public void setExportedName(Name exportedName) {
        this.exportedName = exportedName;
        if (exportedName != null) {
            exportedName.setParent(this);
        }
    }

    /**
     * Returns the local name as a string.
     *
     * @return the local name identifier, or null if not set
     */
    public String getLocalNameString() {
        return localName != null ? localName.getIdentifier() : null;
    }

    /**
     * Returns the exported name as a string.
     *
     * @return the exported name identifier, or null if not set
     */
    public String getExportedNameString() {
        return exportedName != null ? exportedName.getIdentifier() : null;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        if (localName != null) {
            sb.append(localName.toSource(0));
            if (exportedName != null
                    && !localName.getIdentifier().equals(exportedName.getIdentifier())) {
                sb.append(" as ");
                sb.append(exportedName.toSource(0));
            }
        }
        return sb.toString();
    }

    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            if (localName != null) {
                localName.visit(v);
            }
            if (exportedName != null) {
                exportedName.visit(v);
            }
        }
    }
}
