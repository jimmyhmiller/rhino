# ES6 Modules Implementation

**Status**: ES6 module support is **100% complete** (49/49 ES6 tests passing)

This document tracks the implementation status of ES6 modules in Rhino. **Keep this document updated as progress is made.**

> **Note**: The `language/module-code` directory in test262 contains 584 tests total, but only 49 are pure ES6.
> The rest test post-ES6 features: top-level await (ES2022), private fields (ES2022), async functions (ES2017),
> string export names (ES2020), and import attributes (ES2023+).

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
| Circular Dependencies | ✅ Working | Basic circular imports work |

### Test262 Results

| Category | ES6 Tests | Pass Rate | Notes |
|----------|-----------|-----------|-------|
| `language/module-code` | 49 | **49/49 (100%)** | All ES6 tests passing! |

### Recently Fixed

| Test | Fix |
|------|-----|
| `namespace/internals/enumerate-binding-uninit.js` | TDZ check during for-in enumeration via ScriptRuntime.enumNext |
| `namespace/internals/object-keys-binding-uninit.js` | TDZ check in NativeObject.js_keys for module namespaces |
| `namespace/internals/object-propertyIsEnumerable-binding-uninit.js` | TDZ check via getAttributes() and isEnumerable() re-throwing ReferenceError |
| `eval-export-dflt-expr-gen-anon.js` | Set function name to "default" for anonymous generator expressions |
| `instn-named-bndng-dflt-gen-anon.js` | Anonymous generator declarations now hoisted with `*default*` binding |
| `parse-err-hoist-lex-gen.js` | Parser now detects var/generator conflicts in module top-level |
| `namespace/internals/get-own-property-str-found-uninit.js` | TDZ check added to hasOwnProperty for module namespaces |

### Post-ES6 Features (Not in Scope)

These features are tested in `language/module-code` but are **not ES6**:

| Feature | ECMAScript Version | Test Count |
|---------|-------------------|------------|
| Top-level await | ES2022 | ~215 tests |
| Private fields (#field) | ES2022 | ~15 tests |
| Async functions/generators | ES2017/ES2018 | ~5 tests |
| String literal export names | ES2020 | ~10 tests |
| `export * as ns from` | ES2020 | ~2 tests |
| Import attributes | ES2023+ | ~5 tests |

## TDZ (Temporal Dead Zone) Handling

ES6 module namespace objects must throw ReferenceError when accessing uninitialized bindings. This is implemented in:

1. **NativeModuleNamespace.checkBindingTDZ()** - Core TDZ check method that throws if a binding is uninitialized
2. **NativeModuleNamespace.getOwnPropertyDescriptor()** - Calls [[Get]] which triggers TDZ
3. **NativeModuleNamespace.getAttributes()** - Checks TDZ for propertyIsEnumerable
4. **NativeObject.js_keys()** - Calls checkBindingTDZ for each key on module namespaces
5. **NativeObject.isEnumerable()** - Re-throws ReferenceError and TypeError (no longer swallowed)
6. **ScriptRuntime.enumNext()** - Checks TDZ during for-in enumeration for module namespaces

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
