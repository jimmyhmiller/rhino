// Compat table failure reproduction tests
// Run with: java -jar rhino-all/build/libs/rhino-all-*.jar -version 200 compat-test.js
//
// These tests reproduce failures from the kangax ES6 compat table.
// They identify specific ES6 spec compliance gaps in Rhino.

var passed = 0;
var failed = 0;
var errors = [];

function test(category, name, code) {
    try {
        var fn = new Function(code);
        var result = fn();
        if (result === true) {
            passed++;
            print("  PASS: " + name);
        } else {
            failed++;
            errors.push({ category: category, name: name, result: "returned " + result });
            print("  FAIL: " + name + " (returned " + result + ")");
        }
    } catch (e) {
        failed++;
        errors.push({ category: category, name: name, result: e.message });
        print("  FAIL: " + name + " (" + e.message + ")");
    }
}

// =============================================================================
// HIGH PRIORITY: Spread in function calls
// =============================================================================
print("\n=== HIGH PRIORITY: Spread in function calls ===");

test("spread-call", "with arrays, in function calls",
    "return Math.max(...[1, 2, 3]) === 3;");

test("spread-call", "with sparse arrays, in function calls",
    "return Array(...[,,1,2,3]).length === 5;");

test("spread-call", "with strings, in function calls",
    "return Array(...'abc').length === 3 && Array(...'abc')[0] === 'a';");

test("spread-call", "with astral plane strings, in function calls",
    "return Array(...'𠮷𠮶').length === 2 && Array(...'𠮷𠮶')[0] === '𠮷';");

test("spread-call", "with generator instances, in function calls", [
    "function * generator() { yield 1; yield 2; yield 3; }",
    "return Math.max(...generator()) === 3;"
].join('\n'));

test("spread-call", "with generic iterables, in function calls", [
    "var iterable = {};",
    "iterable[Symbol.iterator] = function() {",
    "  var i = 0;",
    "  return { next: function() { return i++ < 3 ? { value: i, done: false } : { done: true }; } };",
    "};",
    "return Math.max(...iterable) === 3;"
].join('\n'));

test("spread-call", "spreading non-iterables is a runtime error", [
    "try {",
    "  Math.max(...13);",
    "  return false;",
    "} catch(e) {",
    "  return e instanceof TypeError;",
    "}"
].join('\n'));

// =============================================================================
// HIGH PRIORITY: Destructuring from iterables
// =============================================================================
print("\n=== HIGH PRIORITY: Destructuring from iterables ===");

test("destruct-iter", "with astral plane strings (declaration)",
    "var [a, b] = '𠮷𠮶'; return a === '𠮷' && b === '𠮶';");

test("destruct-iter", "with generator instances (declaration)", [
    "function * generator() { yield 1; yield 2; }",
    "var [a, b] = generator();",
    "return a === 1 && b === 2;"
].join('\n'));

test("destruct-iter", "with generic iterables (declaration)", [
    "var iterable = {};",
    "iterable[Symbol.iterator] = function() {",
    "  var i = 0;",
    "  return { next: function() { return i++ < 2 ? { value: i, done: false } : { done: true }; } };",
    "};",
    "var [a, b] = iterable;",
    "return a === 1 && b === 2;"
].join('\n'));

test("destruct-iter", "with astral plane strings (assignment)",
    "var a, b; [a, b] = '𠮷𠮶'; return a === '𠮷' && b === '𠮶';");

test("destruct-iter", "with generator instances (assignment)", [
    "function * generator() { yield 1; yield 2; }",
    "var a, b;",
    "[a, b] = generator();",
    "return a === 1 && b === 2;"
].join('\n'));

test("destruct-iter", "with generic iterables (assignment)", [
    "var iterable = {};",
    "iterable[Symbol.iterator] = function() {",
    "  var i = 0;",
    "  return { next: function() { return i++ < 2 ? { value: i, done: false } : { done: true }; } };",
    "};",
    "var a, b;",
    "[a, b] = iterable;",
    "return a === 1 && b === 2;"
].join('\n'));

// =============================================================================
// HIGH PRIORITY: Iterator closing
// =============================================================================
print("\n=== HIGH PRIORITY: Iterator closing ===");

test("iter-close", "closing on break (destructuring declaration)", [
    "var closed = false;",
    "var iter = {",
    "  [Symbol.iterator]: function() {",
    "    return {",
    "      next: function() { return { value: 1, done: false }; },",
    "      'return': function() { closed = true; return {}; }",
    "    };",
    "  }",
    "};",
    "var [a] = iter;",
    "return closed;"
].join('\n'));

test("iter-close", "closing on break (destructuring assignment)", [
    "var closed = false;",
    "var iter = {",
    "  [Symbol.iterator]: function() {",
    "    return {",
    "      next: function() { return { value: 1, done: false }; },",
    "      'return': function() { closed = true; return {}; }",
    "    };",
    "  }",
    "};",
    "var a;",
    "[a] = iter;",
    "return closed;"
].join('\n'));

test("iter-close", "closing on break (for-of)", [
    "var closed = false;",
    "var iter = {",
    "  [Symbol.iterator]: function() {",
    "    return {",
    "      next: function() { return { value: 1, done: false }; },",
    "      'return': function() { closed = true; return {}; }",
    "    };",
    "  }",
    "};",
    "for (var x of iter) break;",
    "return closed;"
].join('\n'));

test("iter-close", "closing on throw (for-of)", [
    "var closed = false;",
    "var iter = {",
    "  [Symbol.iterator]: function() {",
    "    return {",
    "      next: function() { return { value: 1, done: false }; },",
    "      'return': function() { closed = true; return {}; }",
    "    };",
    "  }",
    "};",
    "try {",
    "  for (var x of iter) throw 0;",
    "} catch(e) {}",
    "return closed;"
].join('\n'));

// =============================================================================
// HIGH PRIORITY: Block-level functions
// =============================================================================
print("\n=== HIGH PRIORITY: Block-level functions ===");

test("block-fn", "block-level function declaration", [
    "'use strict';",
    "var passed = false;",
    "if (true) {",
    "  passed = typeof f === 'function';",
    "  function f() {}",
    "}",
    "return passed && typeof f === 'undefined';"
].join('\n'));

// =============================================================================
// MEDIUM PRIORITY: Default parameter TDZ
// =============================================================================
print("\n=== MEDIUM PRIORITY: Default parameter TDZ ===");

test("default-tdz", "temporal dead zone", [
    "try {",
    "  (function(a = a) {})();",
    "  return false;",
    "} catch(e) {",
    "  return e instanceof ReferenceError;",
    "}"
].join('\n'));

test("default-tdz", "separate scope", [
    "var x = 1;",
    "var passed = (function(a = function() { return x; }) {",
    "  var x = 2;",
    "  return a();",
    "}()) === 1;",
    "return passed;"
].join('\n'));

// =============================================================================
// MEDIUM PRIORITY: Arrow function super/new.target
// =============================================================================
print("\n=== MEDIUM PRIORITY: Arrow function super/new.target ===");

test("arrow-super", "lexical super binding in constructors", [
    "class B {",
    "  constructor() { this.x = 1; }",
    "}",
    "class C extends B {",
    "  constructor() {",
    "    var arrow = () => super();",
    "    arrow();",
    "  }",
    "}",
    "return new C().x === 1;"
].join('\n'));

test("arrow-super", "lexical super binding in methods", [
    "class B {",
    "  foo() { return 1; }",
    "}",
    "class C extends B {",
    "  bar() {",
    "    var arrow = () => super.foo();",
    "    return arrow();",
    "  }",
    "}",
    "return new C().bar() === 1;"
].join('\n'));

test("arrow-newtarget", "lexical new.target binding", [
    "function C() {",
    "  var arrow = () => new.target;",
    "  return arrow();",
    "}",
    "return new C() === C && C() === undefined;"
].join('\n'));

// =============================================================================
// MEDIUM PRIORITY: Class name scoping
// =============================================================================
print("\n=== MEDIUM PRIORITY: Class name scoping ===");

test("class-name", "class name is scoped to class body", [
    "class C {",
    "  method() { return typeof C === 'function'; }",
    "}",
    "var D = C;",
    "C = null;",
    "return new D().method();"
].join('\n'));

test("class-name", "class name is const inside body", [
    "class C {",
    "  method() {",
    "    try {",
    "      C = null;",
    "      return false;",
    "    } catch(e) {",
    "      return e instanceof TypeError;",
    "    }",
    "  }",
    "}",
    "return new C().method();"
].join('\n'));

// =============================================================================
// MEDIUM PRIORITY: Generator prototype chain
// =============================================================================
print("\n=== MEDIUM PRIORITY: Generator prototype chain ===");

test("gen-proto", "%GeneratorPrototype%", [
    "function * g() {}",
    "var GeneratorPrototype = Object.getPrototypeOf(g).prototype;",
    "return GeneratorPrototype === Object.getPrototypeOf(g());",
].join('\n'));

test("gen-proto", "%GeneratorPrototype%.constructor", [
    "function * g() {}",
    "var GeneratorPrototype = Object.getPrototypeOf(g).prototype;",
    "var GeneratorFunction = Object.getPrototypeOf(g);",
    "return GeneratorPrototype.constructor === GeneratorFunction;"
].join('\n'));

test("gen-proto", "%GeneratorPrototype% prototype chain", [
    "function * g() {}",
    "var GeneratorPrototype = Object.getPrototypeOf(g).prototype;",
    "var IteratorPrototype = Object.getPrototypeOf(GeneratorPrototype);",
    "return typeof IteratorPrototype[Symbol.iterator] === 'function';"
].join('\n'));

// =============================================================================
// MEDIUM PRIORITY: Rest parameters edge cases
// =============================================================================
print("\n=== MEDIUM PRIORITY: Rest parameters edge cases ===");

test("rest-params", "arguments object interaction", [
    "function f(a, ...rest) {",
    "  a = 2;",
    "  return arguments[0] === 1;",
    "}",
    "return f(1);"
].join('\n'));

test("rest-params", "can't be used in setters", [
    "try {",
    "  eval('({ set s(...args) {} })');",
    "  return false;",
    "} catch(e) {",
    "  return e instanceof SyntaxError;",
    "}"
].join('\n'));

// =============================================================================
// MEDIUM PRIORITY: const edge cases
// =============================================================================
print("\n=== MEDIUM PRIORITY: const edge cases ===");

test("const", "redefining throws SyntaxError", [
    "try {",
    "  eval('const a = 1; const a = 2;');",
    "  return false;",
    "} catch(e) {",
    "  return e instanceof SyntaxError;",
    "}"
].join('\n'));

test("const", "for loop iteration scope", [
    "var funcs = [];",
    "for (const i of [1, 2, 3]) {",
    "  funcs.push(function() { return i; });",
    "}",
    "return funcs[0]() === 1 && funcs[1]() === 2 && funcs[2]() === 3;"
].join('\n'));

// =============================================================================
// Summary
// =============================================================================
print("\n=== SUMMARY ===");
print("Passed: " + passed);
print("Failed: " + failed);
print("Total:  " + (passed + failed));
print("Pass %: " + Math.round(passed / (passed + failed) * 100) + "%");

if (errors.length > 0) {
    print("\n=== FAILURES BY CATEGORY ===");
    var categories = {};
    errors.forEach(function(e) {
        if (!categories[e.category]) categories[e.category] = [];
        categories[e.category].push(e);
    });
    Object.keys(categories).forEach(function(cat) {
        print("\n" + cat + ":");
        categories[cat].forEach(function(e) {
            print("  - " + e.name + ": " + e.result);
        });
    });
}
