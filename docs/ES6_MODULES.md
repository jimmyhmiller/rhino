# ES6 Modules Implementation

**Status**: Core module linking and validation working; ~49% of test262 module tests passing (532/1086)

This document tracks the implementation status of ES6 modules in Rhino. **Keep this document updated as progress is made.**

## Overview

ES6 modules provide static `import`/`export` syntax for JavaScript. This implementation adds:
- Full parsing support for all ES6 module syntax
- AST node classes for import/export declarations
- Runtime infrastructure (ModuleRecord, ModuleLoader interface, namespace objects)
- Context methods for compiling and evaluating modules
- ModuleScope for module environment records with live import binding resolution
- ModuleAnalyzer for extracting import/export metadata from AST
- Test262ModuleLoader for running module tests
- Linking-phase validation per ES6 spec 16.2.1.5.3.1

## Implementation Status

### Completed

| Component | Status | Files |
|-----------|--------|-------|
| AST Nodes | ✅ Complete | `ast/ImportDeclaration.java`, `ast/ImportSpecifier.java`, `ast/ExportDeclaration.java`, `ast/ExportSpecifier.java` |
| Parser | ✅ Complete | `Parser.java` - includes top-level enforcement for import/export |
| TokenStream | ✅ Complete | `TokenStream.java`, `Token.java` |
| IRFactory | ✅ Complete | `IRFactory.java` - exports transform to declarations/EMPTY, imports transform to EMPTY |
| Runtime Classes | ✅ Complete | `es6module/ModuleRecord.java`, `es6module/ModuleLoader.java`, `es6module/NativeModuleNamespace.java` |
| Module Scope | ✅ Complete | `ModuleScope.java` - live import binding resolution |
| Module Analyzer | ✅ Complete | `ModuleAnalyzer.java` |
| Context Integration | ✅ Complete | `Context.java` - `compileModule()`, `evaluateModule()`, `linkAndEvaluateModule()` |
| Linking Validation | ✅ Complete | Validates indirect exports and import bindings during linking (ES6 16.2.1.5.3.1 steps 9, 12) |
| ResolveExport | ✅ Complete | Full algorithm with ambiguity detection and cycle handling |
| Error Messages | ✅ Complete | `Messages.properties` |
| Unit Tests | ✅ Complete | `tests/.../es6/ES6ModulesTest.java` |
| Test262 Support | ✅ Enabled | Module tests run with Test262ModuleLoader |
| Multi-Module Imports | ✅ Working | Import bindings resolve through ModuleScope at runtime |
| Circular Dependencies | ⚠️ Partial | Basic circular imports work; some edge cases need work |

### Test262 Results

| Category | Pass Rate | Notes |
|----------|-----------|-------|
| `language/module-code` | **265/584 (45.38%)** | 319 failing; improved from ~43% with recent fixes |

### Current Limitations

| Limitation | Status | Notes |
|------------|--------|-------|
| Module namespace object internals | ✅ Mostly fixed | `Object.freeze`, `Reflect.set`, `defineProperty` working; TDZ tests remaining |
| Star exports (export * from) | ✅ Fixed | `getExportedNames()` now includes star re-exports; `default` excluded per spec |
| Duplicate export detection | ✅ Fixed | ModuleAnalyzer now validates no duplicate export names |
| Local binding initialization | ❌ Incomplete | `instn-local-bndng-*` tests - let/const/class bindings in modules |
| Function hoisting in modules | ❌ Incomplete | Named default function exports need proper hoisting |
| TDZ for uninitialized bindings | ❌ Incomplete | `*-uninit.js` tests need ReferenceError before initialization |
| Dynamic import() | ❌ Not started | Requires async support |
| Top-level await | ❌ Not started | Requires async support |
| Keyword escape sequences | ❌ Not supported | Parser doesn't reject `\u0065xport` etc. |

## Next Steps (Priority Order)

### 1. Fix TDZ (Temporal Dead Zone) for Uninitialized Bindings (~7 tests)
The remaining `*-uninit.js` tests require proper TDZ handling:
- Accessing uninitialized let/const bindings should throw ReferenceError
- `Object.keys`, `Object.freeze`, etc. on namespace with uninitialized bindings

**Files to modify**: `ModuleScope.java`, `NativeModuleNamespace.java`, `ModuleRecord.java`

### 2. Fix Function Hoisting in Modules (~4 tests)
Named default function exports need proper hoisting:
- `export default function fName() {}` should hoist `fName`
- Function declarations in modules should be hoisted like regular scripts

**Files to modify**: `Context.java` (evaluateModuleInternal), possibly `IRFactory.java`

### 3. Fix Local Binding Initialization (~6 tests)
Tests like `instn-local-bndng-cls.js`, `instn-local-bndng-const.js`, `instn-local-bndng-let.js`:
- Module-local let/const/class declarations need proper TDZ handling

**Files to modify**: `ModuleScope.java`, possibly `Interpreter.java`

### 4. Fix Anonymous Default Export Function/Class Names (~4 tests)
Anonymous default exports should have `.name` property set to "default":
- `export default function() {}` should have `name === "default"`
- `export default class {}` should have `name === "default"`

**Files to modify**: `IRFactory.java`, `ModuleAnalyzer.java`

### 5. Eval in Module Context (remaining eval-* tests)
Several `eval-*` tests that check module behavior in eval contexts.

## Running Module Tests

```bash
# Run all module tests
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="language/module-code/*" --rerun-tasks

# Run with raw output (see actual pass/fail)
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="language/module-code/*" -Dtest262raw --rerun-tasks

# Run specific test category
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="language/module-code/namespace/internals/*" -Dtest262raw --rerun-tasks
```

## Supported Syntax

### Import Declarations

```javascript
// Side-effect only import
import 'module';

// Default import
import foo from 'module';

// Namespace import
import * as ns from 'module';

// Named imports
import { a, b as c } from 'module';

// Default + named imports
import foo, { a, b } from 'module';

// Default + namespace import
import foo, * as ns from 'module';
```

### Export Declarations

```javascript
// Named exports
export { a, b as c };

// Re-export named
export { a, b } from 'module';

// Re-export all
export * from 'module';

// Re-export all as namespace (ES2020)
export * as ns from 'module';

// Default export (expression)
export default 42;

// Default export (function)
export default function() {}

// Default export (class)
export default class {}

// Declaration exports
export function foo() {}
export class Foo {}
export var x = 1;
export let y = 2;
export const z = 3;
```

## Architecture

### AST Nodes

**ImportDeclaration** (Token.IMPORT)
- `moduleSpecifier: String` - The module path
- `defaultImport: Name` - Default import binding (optional)
- `namespaceImport: Name` - Namespace import binding (optional)
- `namedImports: List<ImportSpecifier>` - Named import list

**ImportSpecifier**
- `importedName: Name` - Name in source module
- `localName: Name` - Local binding name

**ExportDeclaration** (Token.EXPORT)
- `isDefault: boolean` - Is this `export default`?
- `declaration: AstNode` - Exported function/class/variable
- `defaultExpression: AstNode` - Expression for `export default expr`
- `namedExports: List<ExportSpecifier>` - Named export list
- `fromModuleSpecifier: String` - For re-exports
- `isStarExport: boolean` - Is this `export *`?
- `starExportAlias: Name` - Alias for `export * as ns`

**ExportSpecifier**
- `localName: Name` - Local binding name
- `exportedName: Name` - Exported name

### Runtime Classes

**ModuleRecord** (`es6module/ModuleRecord.java`)
- Represents an ES6 Abstract Module Record
- Tracks module status: UNLINKED → LINKING → LINKED → EVALUATING → EVALUATED
- Stores export bindings, import/export entries
- Contains ImportEntry and ExportEntry inner classes

**ModuleLoader** (`es6module/ModuleLoader.java`)
- Interface for host-provided module loading
- `resolveModule(specifier, referrer)` - Resolve module specifier to canonical ID
- `loadModule(cx, resolvedSpecifier)` - Load and parse module
- `getCachedModule(resolvedSpecifier)` - Get cached module

**NativeModuleNamespace** (`es6module/NativeModuleNamespace.java`)
- Module Namespace Exotic Object
- Immutable, non-extensible
- Only exposes module's exports
- Has `@@toStringTag` = "Module"

## Usage

### Compiling and Evaluating a Module

```java
try (Context cx = Context.enter()) {
    cx.setLanguageVersion(Context.VERSION_ES6);
    Scriptable scope = cx.initStandardObjects();

    // Compile a module
    ModuleRecord record = cx.compileModule(
        "export const x = 42;",
        "mymodule.mjs",
        1,
        null
    );

    // Link and evaluate the module
    Scriptable namespace = cx.linkAndEvaluateModule(scope, record);

    // Access exports
    Object x = namespace.get("x", namespace);  // 42
}
```

### Parser Entry Point

```java
// Parse as module (strict mode, import/export allowed)
Parser parser = new Parser(compilerEnv);
AstRoot root = parser.parseModule(source, "module.js", 1);

// Check if parsed as module
boolean isModule = root.isModule();
```

### Implementing a ModuleLoader (for multi-module support)

To support modules that import from other modules, implement the `ModuleLoader` interface:

```java
public class FileModuleLoader implements ModuleLoader {
    private final Map<String, ModuleRecord> cache = new HashMap<>();
    private final File baseDir;

    public FileModuleLoader(File baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public String resolveModule(String specifier, ModuleRecord referrer) {
        // Resolve relative/absolute paths
        File resolved = new File(baseDir, specifier);
        return resolved.getAbsolutePath();
    }

    @Override
    public ModuleRecord loadModule(Context cx, String resolvedSpecifier) {
        // Check cache first
        ModuleRecord cached = cache.get(resolvedSpecifier);
        if (cached != null) return cached;

        // Read and compile module
        String source = Files.readString(Path.of(resolvedSpecifier));
        ModuleRecord record = cx.compileModule(source, resolvedSpecifier, 1, null);
        cache.put(resolvedSpecifier, record);
        return record;
    }

    @Override
    public ModuleRecord getCachedModule(String resolvedSpecifier) {
        return cache.get(resolvedSpecifier);
    }
}

// Usage:
cx.setModuleLoader(new FileModuleLoader(new File("/path/to/modules")));
```

## Test262 Coverage

Module tests are now enabled in test262.properties. Run module tests:

```bash
./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \
  -Dtest262filter="language/module-code/*" -Dtest262raw
```

## Files Modified/Created

### New Files
- `rhino/src/main/java/org/mozilla/javascript/ast/ImportDeclaration.java`
- `rhino/src/main/java/org/mozilla/javascript/ast/ImportSpecifier.java`
- `rhino/src/main/java/org/mozilla/javascript/ast/ExportDeclaration.java`
- `rhino/src/main/java/org/mozilla/javascript/ast/ExportSpecifier.java`
- `rhino/src/main/java/org/mozilla/javascript/es6module/ModuleRecord.java`
- `rhino/src/main/java/org/mozilla/javascript/es6module/ModuleLoader.java`
- `rhino/src/main/java/org/mozilla/javascript/es6module/NativeModuleNamespace.java`
- `rhino/src/main/java/org/mozilla/javascript/ModuleScope.java`
- `rhino/src/main/java/org/mozilla/javascript/ModuleAnalyzer.java`
- `tests/src/test/java/org/mozilla/javascript/tests/es6/ES6ModulesTest.java`

### Modified Files
- `rhino/src/main/java/org/mozilla/javascript/Parser.java` - Added module parsing
- `rhino/src/main/java/org/mozilla/javascript/TokenStream.java` - Changed import/export tokens
- `rhino/src/main/java/org/mozilla/javascript/Token.java` - Added keywordToName entries
- `rhino/src/main/java/org/mozilla/javascript/IRFactory.java` - Added transform methods for import/export
- `rhino/src/main/java/org/mozilla/javascript/Context.java` - Added compileModule/evaluateModule/linkAndEvaluateModule
- `rhino/src/main/java/org/mozilla/javascript/ast/ScriptNode.java` - Added isModule flag
- `rhino/src/main/resources/org/mozilla/javascript/resources/Messages.properties` - Added error messages
- `tests/src/test/java/org/mozilla/javascript/tests/Test262SuiteTest.java` - Added module test support
