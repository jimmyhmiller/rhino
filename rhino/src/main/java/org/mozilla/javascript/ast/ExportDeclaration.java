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
 * AST node for an ES6 export declaration.
 *
 * <p>Node type is {@link Token#EXPORT}.
 *
 * <pre><i>ExportDeclaration</i> :
 *       <b>export</b> ExportFromClause FromClause ;
 *       <b>export</b> NamedExports ;
 *       <b>export</b> VariableStatement
 *       <b>export</b> Declaration
 *       <b>export</b> <b>default</b> HoistableDeclaration
 *       <b>export</b> <b>default</b> ClassDeclaration
 *       <b>export</b> <b>default</b> AssignmentExpression ;
 * <i>ExportFromClause</i> :
 *       <b>*</b>
 *       <b>*</b> <b>as</b> IdentifierName
 *       NamedExports
 * <i>NamedExports</i> :
 *       <b>{</b> <b>}</b>
 *       <b>{</b> ExportsList <b>}</b>
 *       <b>{</b> ExportsList <b>,</b> <b>}</b></pre>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code export { a, b as c }} - named exports
 *   <li>{@code export { a, b } from "module"} - re-export named
 *   <li>{@code export * from "module"} - re-export all
 *   <li>{@code export * as ns from "module"} - re-export namespace
 *   <li>{@code export default expr} - default export
 *   <li>{@code export function f() {}} - declaration export
 *   <li>{@code export class C {}} - declaration export
 *   <li>{@code export const x = 1} - variable export
 * </ul>
 */
public class ExportDeclaration extends AstNode {

    private static final List<ExportSpecifier> NO_SPECIFIERS =
            Collections.unmodifiableList(new ArrayList<>());

    private boolean isDefault; // export default ...
    private AstNode declaration; // export function/class/var/let/const
    private AstNode defaultExpression; // export default <expr>
    private List<ExportSpecifier> namedExports; // export { a, b as c }
    private String fromModuleSpecifier; // for re-exports: export ... from "module"
    private boolean isStarExport; // export * from ...
    private Name starExportAlias; // export * as ns from ...

    {
        type = Token.EXPORT;
    }

    public ExportDeclaration() {}

    public ExportDeclaration(int pos) {
        super(pos);
    }

    public ExportDeclaration(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns true if this is a default export.
     *
     * @return true for {@code export default ...}
     */
    public boolean isDefault() {
        return isDefault;
    }

    /**
     * Sets whether this is a default export.
     *
     * @param isDefault true for default export
     */
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    /**
     * Returns the exported declaration (function, class, or variable), or null.
     *
     * @return the declaration being exported
     */
    public AstNode getDeclaration() {
        return declaration;
    }

    /**
     * Sets the exported declaration.
     *
     * @param declaration the function, class, or variable declaration
     */
    public void setDeclaration(AstNode declaration) {
        this.declaration = declaration;
        if (declaration != null) {
            declaration.setParent(this);
        }
    }

    /**
     * Returns the default export expression, or null.
     *
     * @return the default export expression
     */
    public AstNode getDefaultExpression() {
        return defaultExpression;
    }

    /**
     * Sets the default export expression.
     *
     * @param defaultExpression the expression for {@code export default expr}
     */
    public void setDefaultExpression(AstNode defaultExpression) {
        this.defaultExpression = defaultExpression;
        if (defaultExpression != null) {
            defaultExpression.setParent(this);
        }
    }

    /**
     * Returns the list of named export specifiers. Returns an immutable empty list if there are no
     * named exports.
     *
     * @return the named exports
     */
    public List<ExportSpecifier> getNamedExports() {
        return namedExports != null ? namedExports : NO_SPECIFIERS;
    }

    /**
     * Sets the list of named exports.
     *
     * @param namedExports the named exports
     */
    public void setNamedExports(List<ExportSpecifier> namedExports) {
        if (namedExports == null) {
            this.namedExports = null;
        } else {
            if (this.namedExports != null) this.namedExports.clear();
            for (ExportSpecifier spec : namedExports) addNamedExport(spec);
        }
    }

    /**
     * Adds a named export specifier.
     *
     * @param specifier the export specifier to add
     */
    public void addNamedExport(ExportSpecifier specifier) {
        assertNotNull(specifier);
        if (namedExports == null) {
            namedExports = new ArrayList<>();
        }
        namedExports.add(specifier);
        specifier.setParent(this);
    }

    /**
     * Returns the module specifier for re-exports, or null.
     *
     * @return the "from" module specifier
     */
    public String getFromModuleSpecifier() {
        return fromModuleSpecifier;
    }

    /**
     * Sets the module specifier for re-exports.
     *
     * @param fromModuleSpecifier the module specifier string
     */
    public void setFromModuleSpecifier(String fromModuleSpecifier) {
        this.fromModuleSpecifier = fromModuleSpecifier;
    }

    /**
     * Returns true if this is a star export ({@code export * from ...}).
     *
     * @return true for star exports
     */
    public boolean isStarExport() {
        return isStarExport;
    }

    /**
     * Sets whether this is a star export.
     *
     * @param isStarExport true for star exports
     */
    public void setStarExport(boolean isStarExport) {
        this.isStarExport = isStarExport;
    }

    /**
     * Returns the alias for star exports ({@code export * as ns from ...}), or null.
     *
     * @return the star export alias name
     */
    public Name getStarExportAlias() {
        return starExportAlias;
    }

    /**
     * Sets the alias for star exports.
     *
     * @param starExportAlias the alias name
     */
    public void setStarExportAlias(Name starExportAlias) {
        this.starExportAlias = starExportAlias;
        if (starExportAlias != null) {
            starExportAlias.setParent(this);
        }
    }

    /**
     * Returns true if this is a re-export from another module.
     *
     * @return true if this has a "from" clause
     */
    public boolean isReexport() {
        return fromModuleSpecifier != null;
    }

    /**
     * Returns the star export alias as a string.
     *
     * @return the alias identifier, or null
     */
    public String getStarExportAliasString() {
        return starExportAlias != null ? starExportAlias.getIdentifier() : null;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append("export ");

        if (isDefault) {
            sb.append("default ");
            if (declaration != null) {
                sb.append(declaration.toSource(0));
            } else if (defaultExpression != null) {
                sb.append(defaultExpression.toSource(0));
                sb.append(";");
            }
        } else if (isStarExport) {
            sb.append("*");
            if (starExportAlias != null) {
                sb.append(" as ");
                sb.append(starExportAlias.toSource(0));
            }
            sb.append(" from \"");
            sb.append(fromModuleSpecifier);
            sb.append("\";");
        } else if (declaration != null) {
            sb.append(declaration.toSource(0));
        } else if (!getNamedExports().isEmpty() || fromModuleSpecifier != null) {
            sb.append("{ ");
            boolean first = true;
            for (ExportSpecifier spec : getNamedExports()) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(spec.toSource(0));
                first = false;
            }
            sb.append(" }");
            if (fromModuleSpecifier != null) {
                sb.append(" from \"");
                sb.append(fromModuleSpecifier);
                sb.append("\"");
            }
            sb.append(";");
        }

        return sb.toString();
    }

    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            if (declaration != null) {
                declaration.visit(v);
            }
            if (defaultExpression != null) {
                defaultExpression.visit(v);
            }
            for (ExportSpecifier spec : getNamedExports()) {
                spec.visit(v);
            }
            if (starExportAlias != null) {
                starExportAlias.visit(v);
            }
        }
    }
}
