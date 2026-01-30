# ES6 Classes Implementation Plan for Rhino

This document tracks the remaining work to complete ES6 class support in Rhino.

**Async methods are out of scope** (requires async/await feature first).

---

## Current Status

**test262 class tests:** 3,569 / 4,366 failing (81.75%)
**Last updated:** After implementing static method name/length precedence

### What's Working

- [x] Basic class declarations and expressions
- [x] Named and anonymous class expressions
- [x] Constructor methods
- [x] Instance methods
- [x] Static methods
- [x] Getters and setters (instance and static)
- [x] Computed method names (`[expr]() {}`)
- [x] Generator methods (`*method() {}`)
- [x] `extends` clause with prototype chain setup
- [x] `super()` constructor calls
- [x] Default constructor argument forwarding (`...args`)
- [x] Subclassing built-in types (Map, Set, Array, etc.)
- [x] `instanceof` checks
- [x] TypeError when calling constructor without `new`
- [x] TDZ for `this` before `super()` in derived constructors
- [x] Derived class constructor return value handling
- [x] Prototype property is non-writable
- [x] Static name/length methods override constructor properties
- [x] Class constructor restricted properties (no arguments/caller/arity)

---

## Remaining Work (Priority Order)

### Phase 1: Property Descriptors ⚡ HIGH IMPACT

**Problem:** Class methods and accessors don't have correct property descriptors per ES6 spec.

**Failing Tests:**
- `definition/methods.js`
- `definition/accessors.js`
- `definition/getters-prop-desc.js`
- `definition/setters-prop-desc.js`

**Current behavior vs Expected:**

```javascript
class C {
  method() {}
  get x() {}
  set x(v) {}
}

// Methods - EXPECTED:
Object.getOwnPropertyDescriptor(C.prototype, 'method')
// { value: fn, writable: true, enumerable: false, configurable: true }
// 'prototype' in C.prototype.method === false

// Getters/Setters - EXPECTED:
Object.getOwnPropertyDescriptor(C.prototype, 'x')
// { get: fn, set: fn, enumerable: false, configurable: true }
// 'prototype' in getter === false
// 'prototype' in setter === false
```

**Issues to fix:**
1. Methods are currently **enumerable** (should be non-enumerable)
2. Method functions have a **prototype** property (should not have one)
3. Accessor functions have a **prototype** property (should not have one)

**Files to investigate:**
- `ScriptRuntime.createClass()` - how methods are defined on prototype
- `JSFunction` - property creation
- `BaseFunction.createPrototypeProperty()` - when prototype is added

---

### Phase 2: Super Property Access ⚡ HIGH IMPACT

**Problem:** `super.property` and `super.method()` don't work correctly in class methods.

**Failing Tests:**
- `super/in-methods.js`
- `super/in-getter.js`
- `super/in-setter.js`
- `super/in-static-methods.js`
- `super/in-static-getter.js`
- `super/in-static-setter.js`

**Expected behavior:**
```javascript
class B {
  method() { return 1; }
  get x() { return 2; }
}
class C extends B {
  method() {
    super.x;         // Should return 2
    super.method();  // Should return 1
  }
}
new C().method();  // Should work
```

**Implementation notes:**
- `super()` constructor calls already work
- `homeObject` is already set on class methods
- Need to verify `Icode_SUPER_GETPROP` / `Token.SUPER_GETPROP` handling
- Super property should look up from `homeObject.__proto__`

**Files to investigate:**
- `Interpreter.java` - `DoSuperGetProp`, `DoSuperGetElem`
- `BodyCodegen.java` - compiled super property access
- `ScriptRuntime.java` - super property resolution

---

### Phase 3: Static Method name/length Precedence ✅ COMPLETE

**Problem:** Static methods named `length` or `name` should override constructor's built-in properties.

**Solution implemented:**
- Modified `fillClassMembers()` to use `defineOwnProperty()` instead of `put()` for class static methods
- Modified `setSlotValue()` to replace `BuiltInSlot` with regular `Slot` when defining data descriptors
- Added `isClassConstructor()` to `shouldRemoveRestrictedProperties()` to remove `arguments`/`caller`/`arity`
- All 4 precedence tests now pass

---

### Phase 4: Constructor Strict Mode 🔶 MEDIUM

**Problem:** Class constructors should always execute in strict mode, even in non-strict scripts.

**Failing Tests:**
- `definition/constructor-strict-by-default.js` (non-strict mode)

**Expected behavior:**
```javascript
// Even in a non-strict script:
class C {
  constructor() {
    undeclaredVar = 1;  // Should throw ReferenceError
  }
}
```

**Implementation notes:**
- Class constructors should have `isStrict = true` regardless of enclosing scope
- Check `IRFactory.transformClass()` and parser for strict mode handling

---

### Phase 5: Inheritance Validation 🔶 MEDIUM

**Problem:** Invalid `extends` expressions should throw TypeError at class creation time.

**Failing Tests:**
- `definition/invalid-extends.js`

**Expected behavior:**
```javascript
class C extends 42 {}           // TypeError: not a constructor
class C extends Math.abs {}     // TypeError: prototype is not an object

function F() {}
F.prototype = 42;
class C extends F {}            // TypeError: prototype is not an object
```

**Implementation notes:**
- Validate at class creation time in `ScriptRuntime.createClass()`
- Check: extends target is a constructor (or null)
- Check: extends target's `.prototype` is null or an object

---

### Phase 6: Subclassing Edge Cases 🔷 LOWER

**Failing Tests:**
- `subclass/class-definition-null-proto.js`
- `subclass/class-definition-null-proto-this.js`
- `subclass/builtin-objects/ArrayBuffer/regular-subclassing.js`
- `subclass/derived-class-return-override-*.js` (some)

**Issues:**
- `class C extends null {}` needs special handling
- Subclassing ArrayBuffer, Proxy
- More constructor return value edge cases

---

### Phase 7: Generator Methods Edge Cases 🔷 LOWER

**Failing Tests:**
- `definition/methods-gen-yield-*.js` (5 tests)
- `gen-method-static/dflt-params-*.js`

**Issues:**
- Yield expressions in generator methods
- Default parameters with generators
- Yield* with newlines

---

### Phase 8: Destructuring in Method Parameters 🔷 LOWER

**Failing Tests:**
- `dstr/meth-*.js` (many tests)

**Expected behavior:**
```javascript
class C {
  method({a, b}) { return a + b; }
}
```

**Notes:** This may be a general destructuring issue, not class-specific.

---

## Quick Reference

### Running Specific Tests
```bash
# Run specific test pattern
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="language/statements/class/definition/methods*" -Dtest262raw

# Regenerate test262.properties after fixes
RHINO_TEST_JAVA_VERSION=11 ./gradlew :tests:test \
  --tests org.mozilla.javascript.tests.Test262SuiteTest \
  --rerun-tasks -DupdateTest262properties
```

### Key Files
| File | Purpose |
|------|---------|
| `ScriptRuntime.java` | `createClass()` method, super property resolution |
| `IRFactory.java` | Class AST transformation |
| `JSFunction.java` | Function property handling |
| `BaseFunction.java` | Prototype property, `length`/`name` |
| `Interpreter.java` | Super property opcodes |
| `BodyCodegen.java` | Compiled super property handling |
| `CodeGenUtils.java` | Descriptor flags |

---

## Test Progress

| Phase | Tests Fixed | Status |
|-------|-------------|--------|
| Property descriptors | ~10 tests | Not started |
| Super property access | ~7 tests | Not started |
| name/length precedence | 9 tests | ✅ Complete |
| Strict mode | ~2 tests | Not started |
| Invalid extends | ~3 tests | Not started |
