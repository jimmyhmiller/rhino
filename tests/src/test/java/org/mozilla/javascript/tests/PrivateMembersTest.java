package org.mozilla.javascript.tests;

import static org.junit.Assert.*;

import org.junit.Test;
import org.mozilla.javascript.testutils.Utils;

/** Tests for ES2022 private class members (#name syntax). */
public class PrivateMembersTest {

    @Test
    public void testPrivateFieldDeclaration() {
        String script =
                "class Foo {\n"
                        + "    #value = 42;\n"
                        + "    getValue() { return this.#value; }\n"
                        + "}\n"
                        + "var f = new Foo();\n"
                        + "f.getValue();";

        Utils.assertWithAllModes_ES6(42, script);
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

        Utils.assertWithAllModes_ES6(2, script);
    }

    @Test
    public void testPrivateFieldNotVisibleOutside() {
        String script =
                "class Foo {\n"
                        + "    #secret = 'hidden';\n"
                        + "}\n"
                        + "var f = new Foo();\n"
                        + "'#secret' in f;";

        Utils.assertWithAllModes_ES6(false, script);
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

        Utils.assertWithAllModes_ES6("1,2", script);
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

        Utils.assertWithAllModes_ES6(7, script);
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

        Utils.assertWithAllModes_ES6(2, script);
    }

    @Test
    public void testPrivateFieldBrandCheck() {
        // Accessing private field on wrong type should throw TypeError
        String script =
                "class Foo {\n"
                        + "    #x = 1;\n"
                        + "    static getX(obj) { return obj.#x; }\n"
                        + "}\n"
                        + "var f = new Foo();\n"
                        + "var error = '';\n"
                        + "try { Foo.getX({}); } catch(e) { error = e.name; }\n"
                        + "error;";

        Utils.assertWithAllModes_ES6("TypeError", script);
    }

    @Test
    public void testPrivateStaticMethod() {
        String script =
                "class Helper {\n"
                        + "    static #double(x) { return x * 2; }\n"
                        + "    static compute(x) { return Helper.#double(x); }\n"
                        + "}\n"
                        + "Helper.compute(21);";

        Utils.assertWithAllModes_ES6(42, script);
    }
}
