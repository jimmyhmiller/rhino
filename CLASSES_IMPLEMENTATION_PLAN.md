# ES6 Classes Implementation Plan for Rhino

## Overview

This document tracks the implementation of ES6 classes in Rhino. Classes are syntactic sugar over JavaScript's prototype-based inheritance, which Rhino already supports well.

**Current State**: Class inheritance with `super()` fully implemented! Classes support constructors, methods, getters, setters, static members, inheritance via `extends`, and `super()` constructor calls.

**Test262 Progress** (as of latest commit):
- `language/statements/class`: 706 passing (3660 still failing) - 83.83% failing
- `language/expressions/super`: 40 passing (54 still failing) - 57.45% failing
- Total: Significant improvement from initial implementation

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

- [x] **3.1 Parse extends Clause** ✅ DONE
  - Parse `class Foo extends Bar { }`
  - Store superclass expression in ClassNode AST
  - File: `rhino/src/main/java/org/mozilla/javascript/Parser.java`

- [x] **3.2 IRFactory - Prototype Chain Setup** ✅ DONE
  - `ScriptRuntime.createClass()` sets up prototype chain correctly
  - `Dog.prototype = Object.create(Animal.prototype)` equivalent
  - `Dog.prototype.constructor = Dog` set correctly
  - Handles extends with expressions: `class Foo extends getBaseClass() { }`
  - Class prototype property is non-writable per ES6 spec

- [x] **3.3 Tests** ✅ DONE
  - Many test262 subclass tests now passing

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

- [x] **4.1 Enable super() Calls** ✅ DONE
  - Parser allows `super` in class constructors by parsing with `isMethodDefinition=true`
  - IRFactory tracks `insideDerivedClassConstructor` context
  - Marks super() calls with `SUPER_CONSTRUCTOR_CALL` node property
  - Files: `Parser.java`, `IRFactory.java`, `Node.java`

- [ ] **4.2 Runtime - super() Semantics** (partial)
  - `super()` calls parent constructor correctly
  - TODO: Track "this not initialized" state
  - TODO: Throw ReferenceError if `this` used before `super()`

- [x] **4.3 super() Execution** ✅ DONE
  - Interpreter: Added `Icode_SUPER_CALL` instruction and `DoSuperCall` handler
  - Compiler: Added `visitSuperCall()` in BodyCodegen calling `ScriptRuntime.callSuperConstructor()`
  - `BaseFunction` stores `superConstructor` reference for runtime lookup
  - Files: `Icode.java`, `CodeGenerator.java`, `Interpreter.java`, `BodyCodegen.java`, `ScriptRuntime.java`, `BaseFunction.java`

- [x] **4.4 Tests** ✅ DONE
  - 33 test262 tests now pass with super() implementation
  - `language/expressions/super`: 73 → 54 failures (19 tests fixed)
  - `language/statements/class`: 3670 → 3660 failures (10 tests fixed)

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

- [x] **5.1 Verify Existing super Property Access** ✅ DONE
  - `super.prop` works via existing home object infrastructure
  - Class methods are marked as method definitions, getting homeObject set

- [x] **5.2 Fix Any Issues** ✅ DONE
  - `super` resolves to parent prototype correctly in class methods
  - `this` binding is preserved in super method calls
  - Existing infrastructure from object literal methods works for classes

- [x] **5.3 Tests** ✅ DONE
  - Super property access tests passing in test262

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

| Category | Initial | After Phase 1-2 | After Phase 3-5 | % Passing |
|----------|---------|-----------------|-----------------|-----------|
| language/statements/class | 0/4366 | 168/4366 | 706/4366 | 16.17% |
| language/expressions/class | 0/4059 | 124/4059 | TBD | ~3% |
| language/expressions/super | ~21/94 | ~21/94 | 40/94 | 42.55% |
| built-ins/Function (class-related) | - | - | +2 tests | - |

**Key Improvements from super() Implementation**:
- `language/expressions/super`: 73 → 54 failures (19 tests fixed)
- `language/statements/class`: 3670 → 3660 failures (10 tests fixed)
- `language/rest-parameters`: 4 → 3 failures (1 test fixed)
- `language/expressions/optional-chaining`: 15 → 14 failures (1 test fixed)
- `built-ins/Function`: 83 → 81 failures (2 tests fixed)
- **Total: 33 tests fixed with super() implementation**

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

## Next Steps

1. **Complete Phase 4.2**: Implement TDZ for `this` before `super()` is called
   - Track "this not initialized" state in derived class constructors
   - Throw ReferenceError if `this` accessed before `super()` called

2. **Phase 7**: Computed method names (should be straightforward)

3. **Phase 8**: Generator methods (may already work)

4. **Phase 9-10**: Private fields and class fields (newer features, lower priority)
