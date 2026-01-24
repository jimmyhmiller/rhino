/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;

/** Debug tests to understand what's broken with per-iteration loop bindings. */
public class PerIterationDebugTest {

    private Object eval(String script) {
        ContextFactory factory = new ContextFactory();
        try (Context cx = factory.enterContext()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            Scriptable scope = cx.initStandardObjects();
            return cx.evaluateString(scope, script, "test", 1, null);
        }
    }

    // ===== Basic loop tests (no closures) =====

    @Test
    public void simpleForLoop() {
        // Does a basic for loop with let work?
        Object result = eval("var sum = 0; for (let i = 0; i < 3; i++) { sum += i; } sum;");
        System.out.println("simpleForLoop result: " + result);
    }

    @Test
    public void simpleForInLoop() {
        // Does a basic for-in loop with let work?
        Object result = eval("var keys = ''; for (let k in {a:1, b:2}) { keys += k; } keys;");
        System.out.println("simpleForInLoop result: " + result);
    }

    @Test
    public void simpleForOfLoop() {
        // Does a basic for-of loop with let work?
        Object result = eval("var sum = 0; for (let x of [1,2,3]) { sum += x; } sum;");
        System.out.println("simpleForOfLoop result: " + result);
    }

    // ===== Closure tests =====

    @Test
    public void forLoopClosureCapture() {
        // What do closures in for loop actually capture?
        Object result =
                eval(
                        "var funcs = [];\n"
                                + "for (let i = 0; i < 3; i++) {\n"
                                + "    funcs.push(function() { return i; });\n"
                                + "}\n"
                                + "JSON.stringify([funcs[0](), funcs[1](), funcs[2]()]);");
        System.out.println("forLoopClosureCapture result: " + result);
    }

    @Test
    public void forInClosureCapture() {
        // What do closures in for-in loop actually capture?
        Object result =
                eval(
                        "var funcs = [];\n"
                                + "for (let k in {a:1, b:2}) {\n"
                                + "    funcs.push(function() { return k; });\n"
                                + "}\n"
                                + "JSON.stringify([funcs[0](), funcs[1]()]);");
        System.out.println("forInClosureCapture result: " + result);
    }

    @Test
    public void forOfClosureCapture() {
        // What do closures in for-of loop actually capture?
        Object result =
                eval(
                        "var funcs = [];\n"
                                + "for (let x of [1,2,3]) {\n"
                                + "    funcs.push(function() { return x; });\n"
                                + "}\n"
                                + "JSON.stringify([funcs[0](), funcs[1](), funcs[2]()]);");
        System.out.println("forOfClosureCapture result: " + result);
    }

    // ===== Increment/decrement tests =====

    @Test
    public void incrementUndefinedVar() {
        // Basic var increment test
        Object result = eval("var v; ++v;");
        System.out.println("incrementUndefinedVar result: " + result);
    }

    @Test
    public void incrementUndefinedLet() {
        // Basic let increment test
        Object result = eval("let l; ++l;");
        System.out.println("incrementUndefinedLet result: " + result);
    }

    @Test
    public void incrementUndefinedConst() {
        // Basic const increment test - should this throw?
        try {
            Object result = eval("const c = undefined; ++c;");
            System.out.println("incrementUndefinedConst result: " + result);
        } catch (Exception e) {
            System.out.println(
                    "incrementUndefinedConst threw: "
                            + e.getClass().getSimpleName()
                            + ": "
                            + e.getMessage());
        }
    }

    // ===== Arguments tests =====

    @Test
    public void simpleArguments() {
        // Basic arguments access
        Object result = eval("(function(a, b) { return arguments[0] + arguments[1]; })(1, 2);");
        System.out.println("simpleArguments result: " + result);
    }

    @Test
    public void argumentsLength() {
        // Arguments length
        Object result = eval("(function(a, b, c) { return arguments.length; })(1, 2);");
        System.out.println("argumentsLength result: " + result);
    }

    @Test
    public void argumentsInStrictMode() {
        // Arguments in strict mode
        Object result = eval("'use strict'; (function(a, b) { return arguments[0]; })(42);");
        System.out.println("argumentsInStrictMode result: " + result);
    }

    // ===== Break/continue in loops =====

    @Test
    public void forLoopWithBreak() {
        Object result =
                eval(
                        "var funcs = [];\n"
                                + "for (let i = 0; i < 5; i++) {\n"
                                + "    funcs.push(function() { return i; });\n"
                                + "    if (i === 2) break;\n"
                                + "}\n"
                                + "JSON.stringify(funcs.map(function(f) { return f(); }));");
        System.out.println("forLoopWithBreak result: " + result);
    }

    @Test
    public void forLoopWithContinue() {
        Object result =
                eval(
                        "var funcs = [];\n"
                                + "for (let i = 0; i < 5; i++) {\n"
                                + "    if (i === 2) continue;\n"
                                + "    funcs.push(function() { return i; });\n"
                                + "}\n"
                                + "JSON.stringify(funcs.map(function(f) { return f(); }));");
        System.out.println("forLoopWithContinue result: " + result);
    }

    // ===== Let variable access pattern (used in ArgumentsTest) =====

    @Test
    public void letVariableModifiedInFunction() {
        // This is the pattern used in ArgumentsTest
        Object result =
                eval(
                        "let res = '';\n"
                                + "function test() {\n"
                                + "  res += 'hello';\n"
                                + "}\n"
                                + "test();\n"
                                + "res;");
        System.out.println("letVariableModifiedInFunction result: '" + result + "'");
    }

    @Test
    public void letVariableModifiedInFunctionWithArguments() {
        // Closer to ArgumentsTest patterns
        Object result =
                eval(
                        "let res = '';\n"
                                + "function test() {\n"
                                + "  res += arguments.length;\n"
                                + "}\n"
                                + "test(1, 2);\n"
                                + "res;");
        System.out.println("letVariableModifiedInFunctionWithArguments result: '" + result + "'");
    }

    @Test
    public void varVariableModifiedInFunction() {
        // Same but with var (should work)
        Object result =
                eval(
                        "var res = '';\n"
                                + "function test() {\n"
                                + "  res += 'hello';\n"
                                + "}\n"
                                + "test();\n"
                                + "res;");
        System.out.println("varVariableModifiedInFunction result: '" + result + "'");
    }

    // ===== Test262 style tests =====

    @Test
    public void test262ForInLetFreshBinding() {
        // From Test262: language/statements/for-in/head-let-fresh-binding-per-iteration.js
        Object result =
                eval(
                        "var fns = {};\n"
                                + "var obj = Object.create(null);\n"
                                + "obj.a = 1;\n"
                                + "obj.b = 1;\n"
                                + "obj.c = 1;\n"
                                + "for (let x in obj) {\n"
                                + "  fns[x] = function() { return x; };\n"
                                + "}\n"
                                + "JSON.stringify([fns.a(), fns.b(), fns.c()]);");
        System.out.println("test262ForInLetFreshBinding result: " + result);
        // Expected: ["a","b","c"]
    }

    @Test
    public void test262ForOfLetFreshBinding() {
        // Similar test for for-of
        Object result =
                eval(
                        "var fns = [];\n"
                                + "var arr = ['a', 'b', 'c'];\n"
                                + "for (let x of arr) {\n"
                                + "  fns.push(function() { return x; });\n"
                                + "}\n"
                                + "JSON.stringify([fns[0](), fns[1](), fns[2]()]);");
        System.out.println("test262ForOfLetFreshBinding result: " + result);
        // Expected: ["a","b","c"]
    }

    @Test
    public void test262ForInConstFreshBinding() {
        // Test const in for-in - Rhino doesn't support const in for-in/for-of yet
        try {
            Object result =
                    eval(
                            "var fns = {};\n"
                                    + "var obj = Object.create(null);\n"
                                    + "obj.a = 1;\n"
                                    + "obj.b = 1;\n"
                                    + "obj.c = 1;\n"
                                    + "for (const x in obj) {\n"
                                    + "  fns[x] = function() { return x; };\n"
                                    + "}\n"
                                    + "JSON.stringify([fns.a(), fns.b(), fns.c()]);");
            System.out.println("test262ForInConstFreshBinding result: " + result);
        } catch (Exception e) {
            System.out.println(
                    "test262ForInConstFreshBinding: const in for-in not supported: "
                            + e.getMessage());
        }
    }

    @Test
    public void test262ForOfConstFreshBinding() {
        // Test const in for-of - Rhino doesn't support const in for-in/for-of yet
        try {
            Object result =
                    eval(
                            "var fns = [];\n"
                                    + "var arr = ['a', 'b', 'c'];\n"
                                    + "for (const x of arr) {\n"
                                    + "  fns.push(function() { return x; });\n"
                                    + "}\n"
                                    + "JSON.stringify([fns[0](), fns[1](), fns[2]()]);");
            System.out.println("test262ForOfConstFreshBinding result: " + result);
        } catch (Exception e) {
            System.out.println(
                    "test262ForOfConstFreshBinding: const in for-of not supported: "
                            + e.getMessage());
        }
    }

    // ===== Nested scopes =====

    @Test
    public void nestedForLoops() {
        Object result =
                eval(
                        "var funcs = [];\n"
                                + "for (let i = 0; i < 2; i++) {\n"
                                + "    for (let j = 0; j < 2; j++) {\n"
                                + "        funcs.push(function() { return [i, j]; });\n"
                                + "    }\n"
                                + "}\n"
                                + "JSON.stringify(funcs.map(function(f) { return f(); }));");
        System.out.println("nestedForLoops result: " + result);
    }
}
