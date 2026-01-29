# ES6 Classes Implementation Plan for Rhino

## Overview

This document tracks the implementation of ES6 classes in Rhino. Classes are syntactic sugar over JavaScript's prototype-based inheritance, which Rhino already supports well.

**Current State**: Basic class support implemented! Classes can be declared and used with constructors, methods, getters, setters, and static members.

**Test262 Progress**:
- `language/statements/class`: 168 passing (4198 still failing)
- `language/expressions/class`: 124 passing (3935 still failing)
- Total: 292 class tests passing out of ~8,425

**Existing Infrastructure We Can Leverage**:
- `super` keyword works in object literal methods
- Home objects fully implemented for `super` binding
- Method shorthand syntax works (`{foo() {}}`)
- Getters/setters work in object literals
- Computed property names work
- Generator methods work

---

## Phase 1: Basic Class Declarations (No Inheritance)

**Goal**: Parse and execute simple classes with constructors and methods.

```javascript
class Foo {
  constructor(x) {
    this.x = x;
  }
  getX() {
    return this.x;
  }
}
```

### Tasks

- [x] **1.1 Token Updates** ✅ DONE
  - Added `Token.CLASS`, `Token.EXTENDS`, `Token.STATIC` to `Token.java`
  - Updated `TokenStream.java` to return these tokens in ES6 mode
  - Added token names to `typeToName()` and `keywordToName()`

- [x] **1.2 AST Nodes** ✅ DONE
  - Created `ClassNode` AST node (handles both declarations and expressions)
  - Created `ClassElement` AST node (for class methods)
  - Files: `rhino/src/main/java/org/mozilla/javascript/ast/ClassNode.java`
  - Files: `rhino/src/main/java/org/mozilla/javascript/ast/ClassElement.java`

- [x] **1.3 Parser - Class Declaration** ✅ DONE
  - Added `classDeclaration()` method to parse class syntax
  - Added `parseClassElement()` to parse individual class members
  - Added `parseClassMethod()` to parse class methods
  - Added `classPropertyName()` to parse property names
  - Class declarations properly create TDZ bindings with `Token.LET`

- [x] **1.4 Parser - Class Expression** ✅ DONE
  - Class expressions work in expression position
  - Named and anonymous class expressions both work
  - Hooks added in `primaryExpr()` for class expressions

- [x] **1.5 IRFactory Transformation** ✅ DONE
  - Basic transformation of class to constructor function works
  - Constructor is properly extracted from class body
  - Default empty constructor is created when none provided
  - Class declarations use `SETLETINIT` for proper TDZ handling
  - Methods added to prototype via `ScriptRuntime.createClass()`
  - Added `Icode_CLASS_DEF` for interpreter bytecode
  - Added `visitClassLiteral()` in BodyCodegen for JVM bytecode

- [x] **1.6 Basic Tests** ✅ DONE
  - Basic class declaration with constructor works ✅
  - Class expression works ✅
  - Named class expression works ✅
  - Instance methods work ✅
  - Getters/setters work ✅
  - Static methods work ✅
  - Method chaining works ✅

### Verification
```bash
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="language/statements/class/definition/*" -Dtest262raw --rerun-tasks
```

---

## Phase 2: Static Members

**Goal**: Support static methods and static fields.

```javascript
class Foo {
  static staticMethod() {
    return 42;
  }
  static staticField = 'hello';
}
```

### Tasks

- [x] **2.1 Parse Static Methods** ✅ DONE
  - `static` keyword recognized before method definitions
  - `isStatic` flag tracked in ClassElement AST node
  - File: `rhino/src/main/java/org/mozilla/javascript/Parser.java`

- [ ] **2.2 Parse Static Fields** (deferred - newer feature)
  - Parse `static field = value;` syntax
  - This is a newer feature, may defer

- [x] **2.3 IRFactory - Static Members** ✅ DONE
  - Static methods go on constructor function, not prototype
  - Handled by `ScriptRuntime.createClass()` - fills staticMethods on constructor

- [x] **2.4 Tests** ✅ DONE
  - Static methods work in test suite

---

## Phase 3: Inheritance (extends)

**Goal**: Support class inheritance with `extends`.

```javascript
class Animal {
  constructor(name) {
    this.name = name;
  }
  speak() {
    return this.name + ' makes a sound';
  }
}

class Dog extends Animal {
  speak() {
    return this.name + ' barks';
  }
}
```

### Tasks

- [ ] **3.1 Parse extends Clause**
  - Parse `class Foo extends Bar { }`
  - Store superclass expression in AST
  - File: `rhino/src/main/java/org/mozilla/javascript/Parser.java`

- [ ] **3.2 IRFactory - Prototype Chain Setup**
  - Set up `Dog.prototype = Object.create(Animal.prototype)`
  - Set `Dog.prototype.constructor = Dog`
  - Handle extends with expressions: `class Foo extends getBaseClass() { }`

- [ ] **3.3 Tests**
  - Run test262 filter: `language/statements/class/subclass/*`

---

## Phase 4: super() in Constructors

**Goal**: Allow calling parent constructor with `super()`.

```javascript
class Dog extends Animal {
  constructor(name, breed) {
    super(name);  // Call Animal constructor
    this.breed = breed;
  }
}
```

### Tasks

- [ ] **4.1 Enable super() Calls**
  - Currently `super()` reports error "msg.super.shorthand.function"
  - Remove this restriction for class constructors
  - File: `rhino/src/main/java/org/mozilla/javascript/IRFactory.java` (line 955-958)

- [ ] **4.2 Runtime - super() Semantics**
  - `super()` must be called before `this` is accessible in derived class
  - Track "this not initialized" state
  - Throw ReferenceError if `this` used before `super()`
  - File: `rhino/src/main/java/org/mozilla/javascript/ScriptRuntime.java`

- [ ] **4.3 super() Execution**
  - Call parent constructor with correct `this` binding
  - May need new Icode instruction or runtime helper

- [ ] **4.4 Tests**
  - Run test262 filter: `language/statements/class/super/*`
  - Run test262 filter: `language/expressions/super/call-*`

---

## Phase 5: super.method() in Class Methods

**Goal**: Allow calling parent methods via `super.method()`.

```javascript
class Dog extends Animal {
  speak() {
    return super.speak() + ' (woof)';
  }
}
```

### Tasks

- [ ] **5.1 Verify Existing super Property Access**
  - `super.prop` should mostly work via existing home object infrastructure
  - Test current behavior in class context

- [ ] **5.2 Fix Any Issues**
  - Ensure `super` resolves to parent prototype in class methods
  - Ensure `this` binding is preserved in super method calls

- [ ] **5.3 Tests**
  - Run test262 filter: `language/expressions/super/prop-*`

---

## Phase 6: Getters and Setters

**Goal**: Support getter/setter methods in classes.

```javascript
class Foo {
  get value() {
    return this._value;
  }
  set value(v) {
    this._value = v;
  }
}
```

### Tasks

- [x] **6.1 Parse get/set in Class Body** ✅ DONE
  - Reuses existing getter/setter parsing from object literals
  - Properly marked with GET_ENTRY and SET_ENTRY in parser
  - File: `rhino/src/main/java/org/mozilla/javascript/Parser.java`

- [x] **6.2 IRFactory - Define Accessors** ✅ DONE
  - `ScriptRuntime.createClass()` uses `fillObjectLiteral` which handles
    getters/setters via `setGetterOrSetter()` on ScriptableObject

- [x] **6.3 Tests** ✅ DONE
  - Getters and setters work in test suite

---

## Phase 7: Computed Method Names

**Goal**: Support computed property names for methods.

```javascript
const methodName = 'dynamicMethod';
class Foo {
  [methodName]() {
    return 42;
  }
  [Symbol.iterator]() {
    // ...
  }
}
```

### Tasks

- [ ] **7.1 Parse Computed Method Names**
  - Reuse computed property name parsing from object literals
  - Should be straightforward if object literals already work

- [ ] **7.2 Tests**
  - Run test262 filter: `language/statements/class/computed-*`

---

## Phase 8: Generator and Async Methods (Future)

**Goal**: Support generator methods in classes.

```javascript
class Foo {
  *generator() {
    yield 1;
    yield 2;
  }
}
```

### Tasks

- [ ] **8.1 Generator Methods**
  - Parse `*methodName()` in class body
  - Should leverage existing generator infrastructure

- [ ] **8.2 Async Methods** (requires async/await support)
  - Deferred until async/await is implemented

---

## Phase 9: Private Fields and Methods (Future/Optional)

**Goal**: Support private class members (newer ES feature).

```javascript
class Foo {
  #privateField = 42;
  #privateMethod() {
    return this.#privateField;
  }
}
```

This is a more recent addition to JavaScript and may be deferred.

---

## Phase 10: Class Fields (Future/Optional)

**Goal**: Support public instance fields.

```javascript
class Foo {
  instanceField = 42;
  anotherField;
}
```

### Tasks

- [ ] **10.1 Parse Field Declarations**
  - Parse `fieldName = value;` in class body
  - Parse `fieldName;` (no initializer)

- [ ] **10.2 IRFactory - Field Initialization**
  - Fields initialize in constructor, before constructor body runs

---

## Test Progress Tracking

| Category | Before | After | Tests Passing |
|----------|--------|-------|---------------|
| language/statements/class | 0/4366 | 168/4366 | 3.85% |
| language/expressions/class | 0/4059 | 124/4059 | 3.05% |
| language/expressions/super (class-dependent) | ~21/94 | TBD | |

**Total class tests passing**: 292 out of ~8,425

---

## Key Files Reference

| File | Purpose |
|------|---------|
| `Token.java` | Token type definitions |
| `TokenStream.java` | Lexer - keyword recognition |
| `Parser.java` | Parser - syntax analysis |
| `ast/*.java` | AST node definitions |
| `IRFactory.java` | AST to IR transformation |
| `CodeGenerator.java` | IR to bytecode (interpreter) |
| `Interpreter.java` | Bytecode execution |
| `BodyCodegen.java` | IR to JVM bytecode (compiled) |
| `ScriptRuntime.java` | Runtime helpers |

---

## Notes

- Classes must throw TypeError when called without `new` (unlike regular functions)
- Class bodies are implicitly in strict mode
- Class declarations are not hoisted like function declarations
- The `constructor` method name is special and becomes the class's [[Call]] behavior
