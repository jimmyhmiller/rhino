/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests.es6;

import static org.junit.Assert.assertEquals;

import org.junit.Ignore;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.tools.shell.Global;

/** Tests for ES6 class declarations and expressions. */
public class ClassTest {

    private Object eval(String script) {
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            Global global = new Global(cx);
            Scriptable scope = cx.newObject(global);
            scope.setPrototype(global);
            scope.setParentScope(null);
            return cx.evaluateString(scope, script, "test.js", 1, null);
        }
    }

    @Test
    public void testBasicClassDeclaration() {
        // Start simple - just constructor, no methods
        Object result =
                eval(
                        "class Foo {\n"
                                + "  constructor(x) { this.x = x; }\n"
                                + "}\n"
                                + "var f = new Foo(42);\n"
                                + "f.x;");
        assertEquals(42, ((Number) result).intValue());
    }

    @Test
    public void testClassExpression() {
        Object result =
                eval(
                        "var Foo = class {\n"
                                + "  constructor(x) { this.x = x; }\n"
                                + "};\n"
                                + "var f = new Foo(10);\n"
                                + "f.x;");
        assertEquals(10, ((Number) result).intValue());
    }

    @Test
    public void testNamedClassExpression() {
        Object result =
                eval(
                        "var Foo = class Bar {\n"
                                + "  constructor(x) { this.x = x; }\n"
                                + "};\n"
                                + "var f = new Foo(5);\n"
                                + "f.x;");
        assertEquals(5, ((Number) result).intValue());
    }

    @Test
    public void testStaticMethod() {
        Object result =
                eval(
                        "class Foo {\n"
                                + "  static greet() { return 'hello'; }\n"
                                + "}\n"
                                + "Foo.greet();");
        assertEquals("hello", result);
    }

    @Test
    public void testGetter() {
        Object result =
                eval(
                        "class Foo {\n"
                                + "  constructor(x) { this._x = x; }\n"
                                + "  get x() { return this._x * 2; }\n"
                                + "}\n"
                                + "var f = new Foo(21);\n"
                                + "f.x;");
        assertEquals(42, ((Number) result).intValue());
    }

    @Test
    public void testSetter() {
        Object result =
                eval(
                        "class Foo {\n"
                                + "  constructor() { this._x = 0; }\n"
                                + "  set x(val) { this._x = val * 2; }\n"
                                + "  get x() { return this._x; }\n"
                                + "}\n"
                                + "var f = new Foo();\n"
                                + "f.x = 10;\n"
                                + "f.x;");
        assertEquals(20, ((Number) result).intValue());
    }

    @Test
    public void testMultipleMethods() {
        Object result =
                eval(
                        "class Calculator {\n"
                                + "  constructor() { this.result = 0; }\n"
                                + "  add(x) { this.result += x; return this; }\n"
                                + "  multiply(x) { this.result *= x; return this; }\n"
                                + "  getValue() { return this.result; }\n"
                                + "}\n"
                                + "var c = new Calculator();\n"
                                + "c.add(5).multiply(3).add(1).getValue();");
        assertEquals(16, ((Number) result).intValue());
    }

    @Test
    public void testClassExtends() {
        Object result =
                eval(
                        "class Animal {\n"
                                + "  constructor(name) { this.name = name; }\n"
                                + "  speak() { return this.name + ' makes a sound'; }\n"
                                + "}\n"
                                + "class Dog extends Animal {\n"
                                + "  constructor(name) { super(name); }\n"
                                + "  speak() { return this.name + ' barks'; }\n"
                                + "}\n"
                                + "var d = new Dog('Rex');\n"
                                + "d.speak();");
        assertEquals("Rex barks", result);
    }

    @Test
    public void testClassExtendsPrototypeChain() {
        Object result =
                eval(
                        "class Animal {\n"
                                + "  constructor(name) { this.name = name; }\n"
                                + "  speak() { return this.name + ' makes a sound'; }\n"
                                + "}\n"
                                + "class Dog extends Animal {\n"
                                + "  constructor(name) { super(name); }\n"
                                + "}\n"
                                + "var d = new Dog('Rex');\n"
                                + "d.speak();");
        // Dog doesn't override speak(), so it should use Animal's
        assertEquals("Rex makes a sound", result);
    }

    @Test
    public void testClassExtendsInstanceOf() {
        Object result =
                eval(
                        "class Animal {}\n"
                                + "class Dog extends Animal {}\n"
                                + "var d = new Dog();\n"
                                + "(d instanceof Dog) && (d instanceof Animal);");
        assertEquals(true, result);
    }

    @Test
    public void testClassExtendsStaticInheritance() {
        Object result =
                eval(
                        "class Animal {\n"
                                + "  static species() { return 'Animal'; }\n"
                                + "}\n"
                                + "class Dog extends Animal {\n"
                                + "}\n"
                                + "Dog.species();");
        // Static methods should be inherited
        assertEquals("Animal", result);
    }

    @Test
    @Ignore("Known issue: Extending built-in Object doesn't set up prototype chain correctly")
    public void testClassExtendsObject() {
        // Extend built-in Object
        Object result =
                eval(
                        "class Subclass extends Object {}\n"
                                + "var sub = new Subclass();\n"
                                + "(sub instanceof Subclass) && (sub instanceof Object);");
        assertEquals(true, result);
    }

    @Test
    public void testClassExtendsInterpreted() {
        // Test in interpreted mode
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setOptimizationLevel(-1); // Force interpreter mode
            Global global = new Global(cx);
            Scriptable scope = cx.newObject(global);
            scope.setPrototype(global);
            scope.setParentScope(null);
            Object result =
                    cx.evaluateString(
                            scope,
                            "class Animal { speak() { return 'animal'; } }\n"
                                    + "class Dog extends Animal {}\n"
                                    + "var d = new Dog();\n"
                                    + "d.speak();",
                            "test.js",
                            1,
                            null);
            assertEquals("animal", result);
        }
    }

    @Test
    public void testSuperCallInterpreted() {
        // Test super() in interpreter mode
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setOptimizationLevel(-1); // Force interpreter mode
            Global global = new Global(cx);
            Scriptable scope = cx.newObject(global);
            scope.setPrototype(global);
            scope.setParentScope(null);
            Object result =
                    cx.evaluateString(
                            scope,
                            "class Animal {\n"
                                    + "  constructor(name) { this.name = name; }\n"
                                    + "}\n"
                                    + "class Dog extends Animal {\n"
                                    + "  constructor(name) { super(name); }\n"
                                    + "}\n"
                                    + "var d = new Dog('Rex');\n"
                                    + "d.name;",
                            "test.js",
                            1,
                            null);
            assertEquals("Rex", result);
        }
    }

    @Test
    public void testStaticNameMethodOverridesClassName() {
        // static name() method should override the default class name property
        Object result =
                eval(
                        "class A {\n"
                                + "  static name() { return 'custom'; }\n"
                                + "}\n"
                                + "typeof A.name;");
        assertEquals("function", result);
    }

    @Test
    public void testStaticNameMethodPropertyOrder() {
        // static name() method should maintain property order
        Object result =
                eval(
                        "class A {\n"
                                + "  static method() {}\n"
                                + "  static name() {}\n"
                                + "}\n"
                                + "Object.getOwnPropertyNames(A).join(',');");
        assertEquals("length,name,prototype,method", result);
    }

    @Test
    public void testStaticLengthMethodOverridesLength() {
        // static length() method should override the default constructor length property
        Object result =
                eval(
                        "class A {\n"
                                + "  static length() { return 42; }\n"
                                + "}\n"
                                + "typeof A.length;");
        assertEquals("function", result);
    }

    @Test
    public void testDefaultParameters() {
        Object result = eval("function f(x = 10) { return x; } f();");
        assertEquals(10, ((Number) result).intValue());
    }
}
