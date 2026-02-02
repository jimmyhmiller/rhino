/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests.es6;

import static org.junit.Assert.*;

import org.junit.Test;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.ExportDeclaration;
import org.mozilla.javascript.ast.ImportDeclaration;

/**
 * Tests for ES6 module syntax parsing.
 *
 * <p>Note: These tests only verify that the parser can correctly parse module syntax. Full module
 * execution requires the module loader infrastructure which is not yet complete.
 */
public class ES6ModulesTest {

    private AstRoot parseModule(String code) {
        CompilerEnvirons env = new CompilerEnvirons();
        env.setLanguageVersion(Context.VERSION_ES6);
        Parser parser = new Parser(env);
        return parser.parseModule(code, "test.js", 1);
    }

    @Test
    public void testSideEffectOnlyImport() {
        AstRoot root = parseModule("import 'module';");
        assertNotNull(root);
        assertTrue(root.isModule());
        assertEquals(1, root.getStatements().size());
        assertTrue(root.getStatements().get(0) instanceof ImportDeclaration);

        ImportDeclaration imp = (ImportDeclaration) root.getStatements().get(0);
        assertEquals("module", imp.getModuleSpecifier());
        assertTrue(imp.isSideEffectOnly());
    }

    @Test
    public void testDefaultImport() {
        AstRoot root = parseModule("import foo from 'module';");
        assertNotNull(root);

        ImportDeclaration imp = (ImportDeclaration) root.getStatements().get(0);
        assertEquals("module", imp.getModuleSpecifier());
        assertNotNull(imp.getDefaultImport());
        assertEquals("foo", imp.getDefaultImportString());
        assertNull(imp.getNamespaceImport());
        assertTrue(imp.getNamedImports().isEmpty());
    }

    @Test
    public void testNamespaceImport() {
        AstRoot root = parseModule("import * as ns from 'module';");
        assertNotNull(root);

        ImportDeclaration imp = (ImportDeclaration) root.getStatements().get(0);
        assertEquals("module", imp.getModuleSpecifier());
        assertNull(imp.getDefaultImport());
        assertNotNull(imp.getNamespaceImport());
        assertEquals("ns", imp.getNamespaceImportString());
    }

    @Test
    public void testNamedImports() {
        AstRoot root = parseModule("import { a, b as c } from 'module';");
        assertNotNull(root);

        ImportDeclaration imp = (ImportDeclaration) root.getStatements().get(0);
        assertEquals("module", imp.getModuleSpecifier());
        assertNull(imp.getDefaultImport());
        assertNull(imp.getNamespaceImport());
        assertEquals(2, imp.getNamedImports().size());

        assertEquals("a", imp.getNamedImports().get(0).getImportedNameString());
        assertEquals("a", imp.getNamedImports().get(0).getLocalNameString());

        assertEquals("b", imp.getNamedImports().get(1).getImportedNameString());
        assertEquals("c", imp.getNamedImports().get(1).getLocalNameString());
    }

    @Test
    public void testDefaultPlusNamedImports() {
        AstRoot root = parseModule("import foo, { a, b } from 'module';");
        assertNotNull(root);

        ImportDeclaration imp = (ImportDeclaration) root.getStatements().get(0);
        assertEquals("foo", imp.getDefaultImportString());
        assertEquals(2, imp.getNamedImports().size());
    }

    @Test
    public void testDefaultPlusNamespaceImport() {
        AstRoot root = parseModule("import foo, * as ns from 'module';");
        assertNotNull(root);

        ImportDeclaration imp = (ImportDeclaration) root.getStatements().get(0);
        assertEquals("foo", imp.getDefaultImportString());
        assertEquals("ns", imp.getNamespaceImportString());
    }

    @Test
    public void testNamedExports() {
        AstRoot root = parseModule("const a = 1; const b = 2; export { a, b as c };");
        assertNotNull(root);
        assertEquals(3, root.getStatements().size());

        ExportDeclaration exp = (ExportDeclaration) root.getStatements().get(2);
        assertFalse(exp.isDefault());
        assertNull(exp.getDeclaration());
        assertEquals(2, exp.getNamedExports().size());

        assertEquals("a", exp.getNamedExports().get(0).getLocalNameString());
        assertEquals("a", exp.getNamedExports().get(0).getExportedNameString());

        assertEquals("b", exp.getNamedExports().get(1).getLocalNameString());
        assertEquals("c", exp.getNamedExports().get(1).getExportedNameString());
    }

    @Test
    public void testReexportNamed() {
        AstRoot root = parseModule("export { a, b } from 'module';");
        assertNotNull(root);

        ExportDeclaration exp = (ExportDeclaration) root.getStatements().get(0);
        assertEquals("module", exp.getFromModuleSpecifier());
        assertTrue(exp.isReexport());
        assertEquals(2, exp.getNamedExports().size());
    }

    @Test
    public void testReexportStar() {
        AstRoot root = parseModule("export * from 'module';");
        assertNotNull(root);

        ExportDeclaration exp = (ExportDeclaration) root.getStatements().get(0);
        assertTrue(exp.isStarExport());
        assertEquals("module", exp.getFromModuleSpecifier());
        assertNull(exp.getStarExportAlias());
    }

    @Test
    public void testReexportStarAsNamespace() {
        AstRoot root = parseModule("export * as ns from 'module';");
        assertNotNull(root);

        ExportDeclaration exp = (ExportDeclaration) root.getStatements().get(0);
        assertTrue(exp.isStarExport());
        assertEquals("ns", exp.getStarExportAliasString());
    }

    @Test
    public void testExportDefaultExpression() {
        AstRoot root = parseModule("export default 42;");
        assertNotNull(root);

        ExportDeclaration exp = (ExportDeclaration) root.getStatements().get(0);
        assertTrue(exp.isDefault());
        assertNotNull(exp.getDefaultExpression());
        assertNull(exp.getDeclaration());
    }

    @Test
    public void testExportDefaultFunction() {
        AstRoot root = parseModule("export default function() {}");
        assertNotNull(root);

        ExportDeclaration exp = (ExportDeclaration) root.getStatements().get(0);
        assertTrue(exp.isDefault());
        assertNotNull(exp.getDeclaration());
        assertNull(exp.getDefaultExpression());
    }

    @Test
    public void testExportDefaultClass() {
        AstRoot root = parseModule("export default class {}");
        assertNotNull(root);

        ExportDeclaration exp = (ExportDeclaration) root.getStatements().get(0);
        assertTrue(exp.isDefault());
        assertNotNull(exp.getDeclaration());
    }

    @Test
    public void testExportFunction() {
        AstRoot root = parseModule("export function foo() {}");
        assertNotNull(root);

        ExportDeclaration exp = (ExportDeclaration) root.getStatements().get(0);
        assertFalse(exp.isDefault());
        assertNotNull(exp.getDeclaration());
    }

    @Test
    public void testExportClass() {
        AstRoot root = parseModule("export class Foo {}");
        assertNotNull(root);

        ExportDeclaration exp = (ExportDeclaration) root.getStatements().get(0);
        assertFalse(exp.isDefault());
        assertNotNull(exp.getDeclaration());
    }

    @Test
    public void testExportVar() {
        AstRoot root = parseModule("export var x = 1;");
        assertNotNull(root);

        ExportDeclaration exp = (ExportDeclaration) root.getStatements().get(0);
        assertFalse(exp.isDefault());
        assertNotNull(exp.getDeclaration());
    }

    @Test
    public void testExportLet() {
        AstRoot root = parseModule("export let x = 1;");
        assertNotNull(root);

        ExportDeclaration exp = (ExportDeclaration) root.getStatements().get(0);
        assertFalse(exp.isDefault());
        assertNotNull(exp.getDeclaration());
    }

    @Test
    public void testExportConst() {
        AstRoot root = parseModule("export const x = 1;");
        assertNotNull(root);

        ExportDeclaration exp = (ExportDeclaration) root.getStatements().get(0);
        assertFalse(exp.isDefault());
        assertNotNull(exp.getDeclaration());
    }

    @Test
    public void testModuleIsStrictMode() {
        AstRoot root = parseModule("const x = 1;");
        assertNotNull(root);
        assertTrue(root.isInStrictMode());
    }

    @Test
    public void testToSource() {
        AstRoot root = parseModule("import foo from 'module';");
        String source = root.toSource();
        assertTrue(source.contains("import"));
        assertTrue(source.contains("foo"));
        assertTrue(source.contains("from"));
        assertTrue(source.contains("module"));
    }

    @Test
    public void testExportToSource() {
        AstRoot root = parseModule("export const x = 1;");
        String source = root.toSource();
        assertTrue(source.contains("export"));
        assertTrue(source.contains("const"));
        assertTrue(source.contains("x"));
    }

    @Test
    public void testModuleCompilation() {
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            org.mozilla.javascript.es6module.ModuleRecord record =
                    cx.compileModule("export const x = 42;", "test.mjs", 1, null);
            assertNotNull(record);
            assertNotNull(record.getScript());
            assertEquals("test.mjs", record.getSpecifier());
            assertEquals(1, record.getLocalExportEntries().size());
            assertEquals("x", record.getLocalExportEntries().get(0).getExportName());
        }
    }

    @Test
    public void testModuleExecution() {
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            org.mozilla.javascript.Scriptable scope = cx.initStandardObjects();
            org.mozilla.javascript.es6module.ModuleRecord record =
                    cx.compileModule("export const x = 42;", "test.mjs", 1, null);
            org.mozilla.javascript.Scriptable ns = cx.linkAndEvaluateModule(scope, record);
            assertNotNull(ns);
            Object x = ns.get("x", ns);
            assertTrue("Expected numeric value", x instanceof Number);
            assertEquals(42, ((Number) x).intValue());
        }
    }

    @Test
    public void testModuleIsStrict() {
        // Modules are always strict - verify with a strict mode feature
        AstRoot root = parseModule("const x = 1;");
        // Modules should report as strict mode
        assertTrue("Module should be in strict mode", root.isInStrictMode());
    }

    @Test
    public void testModuleDefaultExport() {
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            org.mozilla.javascript.Scriptable scope = cx.initStandardObjects();
            org.mozilla.javascript.es6module.ModuleRecord record =
                    cx.compileModule("export default 'hello';", "test.mjs", 1, null);
            org.mozilla.javascript.Scriptable ns = cx.linkAndEvaluateModule(scope, record);
            assertNotNull(ns);
            assertEquals("hello", ns.get("default", ns));
        }
    }

    @Test
    public void testModuleFunctionExport() {
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            org.mozilla.javascript.Scriptable scope = cx.initStandardObjects();
            org.mozilla.javascript.es6module.ModuleRecord record =
                    cx.compileModule(
                            "export function add(a, b) { return a + b; }", "test.mjs", 1, null);
            org.mozilla.javascript.Scriptable ns = cx.linkAndEvaluateModule(scope, record);
            assertNotNull(ns);
            Object addFn = ns.get("add", ns);
            assertTrue(addFn instanceof org.mozilla.javascript.Function);
        }
    }

    @Test
    public void testModuleWithImport() {
        // This test verifies that modules with import statements compile correctly
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            // Import statement should compile (transform to EMPTY node)
            org.mozilla.javascript.es6module.ModuleRecord record =
                    cx.compileModule("import foo from 'module';", "test.mjs", 1, null);
            assertNotNull(record);
            assertNotNull(record.getScript());
            assertEquals(1, record.getRequestedModules().size());
            assertEquals("module", record.getRequestedModules().get(0));
        }
    }

    @Test
    public void testModuleWithExportAndImport() {
        // This test mirrors test262 eval-export-dflt-cls-anon.js
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            String source =
                    "export default class { valueOf() { return 45; } }\n"
                            + "import C from './eval-export-dflt-cls-anon.js';";
            org.mozilla.javascript.es6module.ModuleRecord record =
                    cx.compileModule(source, "eval-export-dflt-cls-anon.js", 1, null);
            assertNotNull(record);
            assertNotNull(record.getScript());
            assertEquals(1, record.getLocalExportEntries().size());
            assertEquals("default", record.getLocalExportEntries().get(0).getExportName());
            assertEquals(1, record.getRequestedModules().size());
        }
    }

    @Test
    public void testModuleImportFromOtherModule() {
        // Test importing from another module (not self-referential)
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            org.mozilla.javascript.Scriptable scope = cx.initStandardObjects();

            // Set up module loader
            final java.util.Map<String, org.mozilla.javascript.es6module.ModuleRecord> cache =
                    new java.util.HashMap<>();
            cx.setModuleLoader(
                    new org.mozilla.javascript.es6module.ModuleLoader() {
                        @Override
                        public String resolveModule(
                                String specifier,
                                org.mozilla.javascript.es6module.ModuleRecord referrer)
                                throws ModuleResolutionException {
                            return specifier; // Use specifier directly as the resolved path
                        }

                        @Override
                        public org.mozilla.javascript.es6module.ModuleRecord loadModule(
                                Context cx, String resolvedSpecifier) throws ModuleLoadException {
                            return cache.get(resolvedSpecifier);
                        }

                        @Override
                        public org.mozilla.javascript.es6module.ModuleRecord getCachedModule(
                                String resolvedSpecifier) {
                            return cache.get(resolvedSpecifier);
                        }
                    });

            // Create the source module
            org.mozilla.javascript.es6module.ModuleRecord sourceModule =
                    cx.compileModule("export const value = 42;", "source.js", 1, null);
            cache.put("source.js", sourceModule);

            // Create the main module that imports from source
            org.mozilla.javascript.es6module.ModuleRecord mainModule =
                    cx.compileModule("import { value } from 'source.js';", "main.js", 1, null);
            cache.put("main.js", mainModule);

            // Evaluate and check
            cx.linkAndEvaluateModule(scope, mainModule);
            // If we get here without error, the basic import worked
        }
    }

    @Test
    public void testCircularDependencyWithConstExport() {
        // Test circular dependency with const (TDZ semantics)
        // This mirrors the test262 instn-iee-bndng-const.js test
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            org.mozilla.javascript.Scriptable scope = cx.initStandardObjects();

            // Set up module loader
            final java.util.Map<String, org.mozilla.javascript.es6module.ModuleRecord> cache =
                    new java.util.HashMap<>();
            final java.util.Map<String, String> sources = new java.util.HashMap<>();

            // Main module exports const A and imports B from fixture
            // Accessing B before A is initialized should throw ReferenceError
            sources.put(
                    "main.js",
                    "var caught = false;\n"
                            + "var errorName = '';\n"
                            + "try { typeof B; } catch (e) { caught = true; errorName = e.name; }\n"
                            + "import { B } from 'fixture.js';\n"
                            + "export const A = 42;\n"
                            + "export { caught, errorName };");
            // Fixture re-exports A as B
            sources.put("fixture.js", "export { A as B } from 'main.js';");

            cx.setModuleLoader(
                    new org.mozilla.javascript.es6module.ModuleLoader() {
                        @Override
                        public String resolveModule(
                                String specifier,
                                org.mozilla.javascript.es6module.ModuleRecord referrer)
                                throws ModuleResolutionException {
                            return specifier;
                        }

                        @Override
                        public org.mozilla.javascript.es6module.ModuleRecord loadModule(
                                Context cx, String resolvedSpecifier) throws ModuleLoadException {
                            if (!cache.containsKey(resolvedSpecifier)) {
                                String source = sources.get(resolvedSpecifier);
                                if (source != null) {
                                    org.mozilla.javascript.es6module.ModuleRecord record =
                                            cx.compileModule(source, resolvedSpecifier, 1, null);
                                    cache.put(resolvedSpecifier, record);
                                }
                            }
                            return cache.get(resolvedSpecifier);
                        }

                        @Override
                        public org.mozilla.javascript.es6module.ModuleRecord getCachedModule(
                                String resolvedSpecifier) {
                            return cache.get(resolvedSpecifier);
                        }
                    });

            // Compile and cache main module
            org.mozilla.javascript.es6module.ModuleRecord mainModule =
                    cx.compileModule(sources.get("main.js"), "main.js", 1, null);
            cache.put("main.js", mainModule);

            // Evaluate
            org.mozilla.javascript.Scriptable ns = cx.linkAndEvaluateModule(scope, mainModule);

            // Check that ReferenceError was caught
            Object caught = ns.get("caught", ns);
            Object errorName = ns.get("errorName", ns);
            assertTrue(
                    "Accessing B before A is initialized should throw an error",
                    Boolean.TRUE.equals(caught));
            assertEquals(
                    "Should be a ReferenceError, not " + errorName, "ReferenceError", errorName);

            // After evaluation, A should be available
            Object a = ns.get("A", ns);
            assertEquals(42, ((Number) a).intValue());
        }
    }

    @Test
    public void testCircularDependencyWithFunctionExport() {
        // Test circular dependency - a re-exports from b, b re-exports from a
        // This tests ES6 live binding semantics with hoisted function
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            org.mozilla.javascript.Scriptable scope = cx.initStandardObjects();

            // Set up module loader
            final java.util.Map<String, org.mozilla.javascript.es6module.ModuleRecord> cache =
                    new java.util.HashMap<>();
            final java.util.Map<String, String> sources = new java.util.HashMap<>();

            // Module a exports function A and imports B from b
            sources.put("a.js", "export function A() { return 77; }\nimport { B } from 'b.js';");
            // Module b re-exports A as B from a
            sources.put("b.js", "export { A as B } from 'a.js';");

            cx.setModuleLoader(
                    new org.mozilla.javascript.es6module.ModuleLoader() {
                        @Override
                        public String resolveModule(
                                String specifier,
                                org.mozilla.javascript.es6module.ModuleRecord referrer)
                                throws ModuleResolutionException {
                            return specifier;
                        }

                        @Override
                        public org.mozilla.javascript.es6module.ModuleRecord loadModule(
                                Context cx, String resolvedSpecifier) throws ModuleLoadException {
                            if (!cache.containsKey(resolvedSpecifier)) {
                                String source = sources.get(resolvedSpecifier);
                                if (source != null) {
                                    org.mozilla.javascript.es6module.ModuleRecord record =
                                            cx.compileModule(source, resolvedSpecifier, 1, null);
                                    cache.put(resolvedSpecifier, record);
                                }
                            }
                            return cache.get(resolvedSpecifier);
                        }

                        @Override
                        public org.mozilla.javascript.es6module.ModuleRecord getCachedModule(
                                String resolvedSpecifier) {
                            return cache.get(resolvedSpecifier);
                        }
                    });

            // Compile and cache main module
            org.mozilla.javascript.es6module.ModuleRecord mainModule =
                    cx.compileModule(sources.get("a.js"), "a.js", 1, null);
            cache.put("a.js", mainModule);

            // Evaluate - this should work due to function hoisting
            org.mozilla.javascript.Scriptable ns = cx.linkAndEvaluateModule(scope, mainModule);

            // The function A should be available
            Object aFn = ns.get("A", ns);
            assertTrue("A should be a function", aFn instanceof org.mozilla.javascript.Function);

            // Call A() and check result
            org.mozilla.javascript.Function fn = (org.mozilla.javascript.Function) aFn;
            Object result = fn.call(cx, scope, scope, new Object[0]);
            assertEquals(77, ((Number) result).intValue());
        }
    }
}
