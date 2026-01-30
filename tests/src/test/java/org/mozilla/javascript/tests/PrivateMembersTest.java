package org.mozilla.javascript.tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.mozilla.javascript.*;
import org.mozilla.javascript.testutils.Utils;

/**
 * Tests for ES2022 private class members (#name syntax). Currently only tests in interpreted mode
 * since the optimizer (BodyCodegen) support is not yet implemented.
 */
public class PrivateMembersTest {

    /** Run script in interpreted mode only (optimizer not yet supported for private members). */
    private Object runScript(String script) {
        Utils.runWithMode(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ES6);
                    final Scriptable scope = cx.initStandardObjects();
                    return cx.evaluateString(scope, script, "test.js", 1, null);
                },
                true); // interpreted mode
        // Utils.runWithMode doesn't return a value, so we need to run again to get result
        final Object[] result = new Object[1];
        Utils.runWithMode(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ES6);
                    final Scriptable scope = cx.initStandardObjects();
                    result[0] = cx.evaluateString(scope, script, "test.js", 1, null);
                    return null;
                },
                true);
        return result[0];
    }

    @Test
    public void testPrivateFieldDeclaration() {
        String script =
                "class Foo {\n"
                        + "    #value = 42;\n"
                        + "    getValue() { return this.#value; }\n"
                        + "}\n"
                        + "var f = new Foo();\n"
                        + "f.getValue();";

        Object result = runScript(script);
        assertEquals(42.0, ((Number) result).doubleValue(), 0.001);
    }

    @Test
    public void testPrivateFieldAssignment() {
        String script =
                "class Counter {\n"
                        + "    #count = 0;\n"
                        + "    increment() {\n"
                        + "        this.#count = this.#count + 1;\n"
                        + "        return this.#count;\n"
                        + "    }\n"
                        + "}\n"
                        + "var c = new Counter();\n"
                        + "c.increment();\n"
                        + "c.increment();";

        Object result = runScript(script);
        assertEquals(2.0, ((Number) result).doubleValue(), 0.001);
    }

    @Test
    public void testPrivateFieldNotVisibleOutside() {
        String script =
                "class Foo {\n"
                        + "    #secret = 'hidden';\n"
                        + "}\n"
                        + "var f = new Foo();\n"
                        + "'#secret' in f;";

        Object result = runScript(script);
        assertEquals(Boolean.FALSE, result);
    }

    @Test
    public void testPrivateFieldPerInstance() {
        String script =
                "class Box {\n"
                        + "    #value;\n"
                        + "    constructor(v) { this.#value = v; }\n"
                        + "    getValue() { return this.#value; }\n"
                        + "}\n"
                        + "var a = new Box(1);\n"
                        + "var b = new Box(2);\n"
                        + "[a.getValue(), b.getValue()].join(',');";

        Object result = runScript(script);
        assertEquals("1,2", result);
    }

    @Test
    public void testPrivateMethod() {
        String script =
                "class Calculator {\n"
                        + "    #add(a, b) { return a + b; }\n"
                        + "    sum(a, b) { return this.#add(a, b); }\n"
                        + "}\n"
                        + "var calc = new Calculator();\n"
                        + "calc.sum(3, 4);";

        Object result = runScript(script);
        assertEquals(7.0, ((Number) result).doubleValue(), 0.001);
    }

    @Test
    public void testStaticPrivateField() {
        // Note: Using explicit assignment instead of ++ since increment on private fields
        // is not yet supported in the parser
        String script =
                "class Counter {\n"
                        + "    static #instanceCount = 0;\n"
                        + "    constructor() { Counter.#instanceCount = Counter.#instanceCount + 1; }\n"
                        + "    static getCount() { return Counter.#instanceCount; }\n"
                        + "}\n"
                        + "new Counter();\n"
                        + "new Counter();\n"
                        + "Counter.getCount();";

        Object result = runScript(script);
        assertEquals(2.0, ((Number) result).doubleValue(), 0.001);
    }
}
