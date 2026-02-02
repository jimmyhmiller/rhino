/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.ClassNode;
import org.mozilla.javascript.ast.ExportDeclaration;
import org.mozilla.javascript.ast.ExportSpecifier;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.ImportDeclaration;
import org.mozilla.javascript.ast.ImportSpecifier;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.VariableDeclaration;
import org.mozilla.javascript.ast.VariableInitializer;
import org.mozilla.javascript.es6module.ModuleRecord;

/**
 * Analyzes a module AST to extract import and export entries.
 *
 * <p>This class processes ImportDeclaration and ExportDeclaration AST nodes to populate the
 * ModuleRecord with the necessary metadata for module linking.
 */
public class ModuleAnalyzer {

    /**
     * Analyzes a module AST and populates the ModuleRecord with import/export entries.
     *
     * @param ast the module AST
     * @param moduleRecord the module record to populate
     */
    public static void analyze(AstRoot ast, ModuleRecord moduleRecord) {
        for (AstNode statement : ast.getStatements()) {
            if (statement instanceof ImportDeclaration) {
                analyzeImport((ImportDeclaration) statement, moduleRecord);
            } else if (statement instanceof ExportDeclaration) {
                analyzeExport((ExportDeclaration) statement, moduleRecord);
            }
        }
    }

    /**
     * Analyzes an import declaration and adds import entries to the module record.
     *
     * @param imp the import declaration
     * @param moduleRecord the module record
     */
    private static void analyzeImport(ImportDeclaration imp, ModuleRecord moduleRecord) {
        String moduleSpecifier = imp.getModuleSpecifier();
        moduleRecord.addRequestedModule(moduleSpecifier);

        // Default import: import foo from 'module'
        if (imp.getDefaultImport() != null) {
            String localName = imp.getDefaultImportString();
            moduleRecord.addImportEntry(
                    new ModuleRecord.ImportEntry(moduleSpecifier, "default", localName));
        }

        // Namespace import: import * as ns from 'module'
        if (imp.getNamespaceImport() != null) {
            String localName = imp.getNamespaceImportString();
            moduleRecord.addImportEntry(
                    new ModuleRecord.ImportEntry(moduleSpecifier, null, localName));
        }

        // Named imports: import { a, b as c } from 'module'
        for (ImportSpecifier spec : imp.getNamedImports()) {
            String importedName = spec.getImportedNameString();
            String localName = spec.getLocalNameString();
            moduleRecord.addImportEntry(
                    new ModuleRecord.ImportEntry(moduleSpecifier, importedName, localName));
        }
    }

    /**
     * Analyzes an export declaration and adds export entries to the module record.
     *
     * @param exp the export declaration
     * @param moduleRecord the module record
     */
    private static void analyzeExport(ExportDeclaration exp, ModuleRecord moduleRecord) {
        String fromModule = exp.getFromModuleSpecifier();

        if (fromModule != null) {
            moduleRecord.addRequestedModule(fromModule);
        }

        // export default ...
        if (exp.isDefault()) {
            analyzeDefaultExport(exp, moduleRecord);
            return;
        }

        // export * from 'module' or export * as ns from 'module'
        if (exp.isStarExport()) {
            analyzeStarExport(exp, moduleRecord, fromModule);
            return;
        }

        // export { a, b as c } or export { a, b } from 'module'
        if (!exp.getNamedExports().isEmpty()) {
            analyzeNamedExports(exp, moduleRecord, fromModule);
            return;
        }

        // export function foo() {} or export class Foo {} or export var/let/const
        if (exp.getDeclaration() != null) {
            analyzeDeclarationExport(exp, moduleRecord);
        }
    }

    /**
     * Analyzes a default export.
     *
     * @param exp the export declaration
     * @param moduleRecord the module record
     */
    private static void analyzeDefaultExport(ExportDeclaration exp, ModuleRecord moduleRecord) {
        // export default function foo() {} - the local name is the function name
        // export default function() {} - the local name is "*default*"
        // export default class Foo {} - the local name is the class name
        // export default expr - the local name is "*default*"
        String localName = "*default*";

        AstNode decl = exp.getDeclaration();
        if (decl != null) {
            if (decl instanceof FunctionNode) {
                FunctionNode fn = (FunctionNode) decl;
                if (fn.getFunctionName() != null) {
                    localName = fn.getFunctionName().getIdentifier();
                }
            } else if (decl instanceof ClassNode) {
                ClassNode cn = (ClassNode) decl;
                if (cn.getClassName() != null) {
                    localName = cn.getClassNameString();
                }
            }
        }

        moduleRecord.addLocalExportEntry(
                new ModuleRecord.ExportEntry("default", null, null, localName));
    }

    /**
     * Analyzes a star export (export * from 'module').
     *
     * @param exp the export declaration
     * @param moduleRecord the module record
     * @param fromModule the source module
     */
    private static void analyzeStarExport(
            ExportDeclaration exp, ModuleRecord moduleRecord, String fromModule) {
        Name alias = exp.getStarExportAlias();
        if (alias != null) {
            // export * as ns from 'module'
            moduleRecord.addIndirectExportEntry(
                    new ModuleRecord.ExportEntry(alias.getIdentifier(), fromModule, "*", null));
        } else {
            // export * from 'module'
            moduleRecord.addStarExportEntry(
                    new ModuleRecord.ExportEntry(null, fromModule, "*", null));
        }
    }

    /**
     * Analyzes named exports.
     *
     * @param exp the export declaration
     * @param moduleRecord the module record
     * @param fromModule the source module (null for local exports)
     */
    private static void analyzeNamedExports(
            ExportDeclaration exp, ModuleRecord moduleRecord, String fromModule) {
        for (ExportSpecifier spec : exp.getNamedExports()) {
            String localName = spec.getLocalNameString();
            String exportedName = spec.getExportedNameString();

            if (fromModule != null) {
                // export { a, b as c } from 'module' - indirect export
                moduleRecord.addIndirectExportEntry(
                        new ModuleRecord.ExportEntry(exportedName, fromModule, localName, null));
            } else {
                // export { a, b as c } - local export
                moduleRecord.addLocalExportEntry(
                        new ModuleRecord.ExportEntry(exportedName, null, null, localName));
            }
        }
    }

    /**
     * Analyzes a declaration export (export function/class/var/let/const).
     *
     * @param exp the export declaration
     * @param moduleRecord the module record
     */
    private static void analyzeDeclarationExport(ExportDeclaration exp, ModuleRecord moduleRecord) {
        AstNode decl = exp.getDeclaration();

        if (decl instanceof FunctionNode) {
            FunctionNode fn = (FunctionNode) decl;
            String name = fn.getFunctionName().getIdentifier();
            moduleRecord.addLocalExportEntry(new ModuleRecord.ExportEntry(name, null, null, name));
        } else if (decl instanceof ClassNode) {
            ClassNode cn = (ClassNode) decl;
            String name = cn.getClassNameString();
            moduleRecord.addLocalExportEntry(new ModuleRecord.ExportEntry(name, null, null, name));
        } else if (decl instanceof VariableDeclaration) {
            VariableDeclaration vars = (VariableDeclaration) decl;
            for (VariableInitializer vi : vars.getVariables()) {
                AstNode target = vi.getTarget();
                if (target instanceof Name) {
                    String name = ((Name) target).getIdentifier();
                    moduleRecord.addLocalExportEntry(
                            new ModuleRecord.ExportEntry(name, null, null, name));
                }
                // TODO: Handle destructuring patterns
            }
        }
    }
}
