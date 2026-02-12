/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.node.module;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.es6module.ModuleLoader;
import org.mozilla.javascript.es6module.ModuleRecord;

/**
 * ES6 {@link ModuleLoader} implementation that uses the Node.js module resolution algorithm.
 *
 * <p>Supports CJS-to-ESM interop: when an ESM import resolves to a CommonJS file, the CJS module is
 * executed with full {@code require}/{@code module}/{@code exports} support and its {@code
 * module.exports} is re-exported as the ESM {@code default} export.
 */
public class NodeModuleLoader implements ModuleLoader {

    private static final Set<String> NODE_BUILTINS = new HashSet<>();

    static {
        String[] builtins = {
            "assert",
            "async_hooks",
            "buffer",
            "child_process",
            "cluster",
            "console",
            "constants",
            "crypto",
            "dgram",
            "diagnostics_channel",
            "dns",
            "domain",
            "events",
            "fs",
            "http",
            "http2",
            "https",
            "inspector",
            "module",
            "net",
            "os",
            "path",
            "perf_hooks",
            "process",
            "punycode",
            "querystring",
            "readline",
            "repl",
            "stream",
            "string_decoder",
            "sys",
            "timers",
            "tls",
            "trace_events",
            "tty",
            "url",
            "util",
            "v8",
            "vm",
            "wasi",
            "worker_threads",
            "zlib"
        };
        for (String b : builtins) {
            NODE_BUILTINS.add(b);
        }
    }

    static boolean isNodeBuiltin(String specifier) {
        if (specifier.startsWith("node:")) {
            return true;
        }
        return NODE_BUILTINS.contains(specifier);
    }

    private static final String BUILTIN_PREFIX = "__rhino_node_builtin__:";

    private final NodeFileSystem fs;
    private final NodeModuleResolver resolver;
    private final ConcurrentHashMap<String, ModuleRecord> esmCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> cjsCache = new ConcurrentHashMap<>();
    private Scriptable globalScope;
    private boolean stubNodeBuiltins = false;

    public void setStubNodeBuiltins(boolean stub) {
        this.stubNodeBuiltins = stub;
    }

    public NodeModuleLoader() {
        this(new DefaultNodeFileSystem());
    }

    public NodeModuleLoader(NodeFileSystem fs) {
        this.fs = fs;
        PackageJsonReader pkgReader = new PackageJsonReader(fs);
        this.resolver = new NodeModuleResolver(fs, pkgReader);
    }

    public NodeModuleLoader(NodeFileSystem fs, NodeModuleResolver resolver) {
        this.fs = fs;
        this.resolver = resolver;
    }

    /**
     * Set the global scope, enabling CJS-to-ESM interop. Must be called before loading any modules
     * that import CJS packages.
     */
    public void setGlobalScope(Scriptable scope) {
        this.globalScope = scope;
        installCjsHelper(scope);
    }

    @Override
    public String resolveModule(String specifier, ModuleRecord referrer)
            throws ModuleResolutionException {
        if (stubNodeBuiltins && isNodeBuiltin(specifier)) {
            return BUILTIN_PREFIX + specifier;
        }

        try {
            String parentPath;
            if (referrer != null && referrer.getSpecifier() != null) {
                parentPath = referrer.getSpecifier();
            } else {
                parentPath = fs.getAbsolutePath(".");
                parentPath = fs.resolve(parentPath, "__entry__.mjs");
            }

            ResolvedModule resolved = resolver.esmResolve(specifier, parentPath);
            return resolved.getPath();
        } catch (NodeModuleResolver.ModuleNotFoundException e) {
            throw new ModuleResolutionException(
                    "Cannot resolve module '" + specifier + "': " + e.getMessage(), e);
        }
    }

    @Override
    public ModuleRecord loadModule(Context cx, String resolvedSpecifier)
            throws ModuleLoadException {
        ModuleRecord cached = esmCache.get(resolvedSpecifier);
        if (cached != null) {
            return cached;
        }

        if (resolvedSpecifier.startsWith(BUILTIN_PREFIX)) {
            String name = resolvedSpecifier.substring(BUILTIN_PREFIX.length());
            if (name.startsWith("node:")) name = name.substring(5);
            String stubSource = getBuiltinStubSource(name);
            String source =
                    "var __stub__ = "
                            + stubSource
                            + ";\n"
                            + "export default __stub__;\n"
                            + "var __keys__ = Object.keys(__stub__);\n"
                            + "for (var __i__ = 0; __i__ < __keys__.length; __i__++) {\n"
                            + "  // named exports handled below\n"
                            + "}\n";
            // Build source with explicit named exports
            StringBuilder sb = new StringBuilder();
            sb.append("var __stub__ = ").append(stubSource).append(";\n");
            sb.append("export default __stub__;\n");
            // We need to know the keys statically for ESM exports, so parse them from the stub
            String[] keys = extractStubKeys(stubSource);
            for (String key : keys) {
                if (!"default".equals(key) && isValidJsIdentifier(key)) {
                    sb.append("export var ")
                            .append(key)
                            .append(" = __stub__.")
                            .append(key)
                            .append(";\n");
                }
            }
            ModuleRecord record = cx.compileModule(sb.toString(), resolvedSpecifier, 1, null);
            esmCache.put(resolvedSpecifier, record);
            return record;
        }

        try {
            String source;
            ResolvedModule.ModuleFormat format = resolver.esmFileFormat(resolvedSpecifier);

            if (format == ResolvedModule.ModuleFormat.JSON) {
                source = fs.readFile(resolvedSpecifier);
                source = "const __json__ = " + source + ";\nexport default __json__;\n";
            } else if (format == ResolvedModule.ModuleFormat.COMMONJS && globalScope != null) {
                // CJS-to-ESM interop: synthetic wrapper that delegates to __rhinoCjsHelper__
                String escapedPath = escapeJsString(resolvedSpecifier);
                StringBuilder sb = new StringBuilder();
                sb.append("var __cjs_exports__ = __rhinoCjsHelper__(\"")
                        .append(escapedPath)
                        .append("\");\n");
                sb.append("export default __cjs_exports__;\n");

                // Generate synthetic named exports from CJS module.exports properties
                Object exports = loadCjsModuleExports(cx, resolvedSpecifier);
                if (exports instanceof Scriptable) {
                    Scriptable exportsObj = (Scriptable) exports;
                    for (Object id : ScriptableObject.getPropertyIds(exportsObj)) {
                        if (id instanceof String) {
                            String name = (String) id;
                            if (!"default".equals(name) && isValidJsIdentifier(name)) {
                                sb.append("export var ")
                                        .append(name)
                                        .append(" = __cjs_exports__.")
                                        .append(name)
                                        .append(";\n");
                            }
                        }
                    }
                }
                source = sb.toString();
            } else {
                source = fs.readFile(resolvedSpecifier);
            }

            ModuleRecord record = cx.compileModule(source, resolvedSpecifier, 1, null);
            esmCache.put(resolvedSpecifier, record);
            return record;
        } catch (IOException e) {
            throw new ModuleLoadException(
                    "Cannot load module '" + resolvedSpecifier + "': " + e.getMessage(), e);
        }
    }

    @Override
    public ModuleRecord getCachedModule(String resolvedSpecifier) {
        return esmCache.get(resolvedSpecifier);
    }

    /**
     * Load and execute a CJS module, returning its {@code module.exports}. Handles circular
     * dependencies by pre-populating the cache with a partial exports object.
     */
    Object loadCjsModuleExports(Context cx, String absolutePath) {
        Object cached = cjsCache.get(absolutePath);
        if (cached != null) {
            return cached;
        }

        try {
            String source = fs.readFile(absolutePath);
            String dirname = fs.dirname(absolutePath);

            // JSON files
            if (absolutePath.endsWith(".json")) {
                Script jsonScript = cx.compileString("(" + source + ")", absolutePath, 1, null);
                Object result = jsonScript.exec(cx, globalScope, globalScope);
                cjsCache.put(absolutePath, result);
                return result;
            }

            // Wrap CJS source in an IIFE that receives module/exports/require/__filename/__dirname
            String wrapped =
                    "(function(exports, require, module, __filename, __dirname) {\n"
                            + source
                            + "\n})";
            Script script = cx.compileString(wrapped, absolutePath, 0, null);
            Object fnObj = script.exec(cx, globalScope, globalScope);

            if (!(fnObj instanceof Function)) {
                throw new RuntimeException(
                        "CJS wrapper did not produce a function for: " + absolutePath);
            }
            Function fn = (Function) fnObj;

            // Create module and exports objects
            ScriptableObject moduleObj = (ScriptableObject) cx.newObject(globalScope);
            Scriptable exportsObj = cx.newObject(globalScope);
            moduleObj.put("exports", moduleObj, exportsObj);

            // Create require function scoped to this module's directory
            BaseFunction requireFn = createRequireFunction(absolutePath);

            // Pre-populate cache for circular dependency support
            cjsCache.put(absolutePath, exportsObj);

            // Execute the CJS module
            fn.call(
                    cx,
                    globalScope,
                    exportsObj,
                    new Object[] {exportsObj, requireFn, moduleObj, absolutePath, dirname});

            // Get final module.exports (may have been reassigned via module.exports = ...)
            Object result = moduleObj.get("exports", moduleObj);
            cjsCache.put(absolutePath, result);
            return result;

        } catch (IOException e) {
            throw new RuntimeException("Cannot load CJS module: " + absolutePath, e);
        }
    }

    private BaseFunction createRequireFunction(String parentPath) {
        NodeModuleLoader self = this;
        BaseFunction requireFn =
                new BaseFunction() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public Object call(
                            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                        String id = Context.toString(args[0]);
                        if (self.stubNodeBuiltins && isNodeBuiltin(id)) {
                            return self.getNodeBuiltinStub(cx, id);
                        }
                        ResolvedModule resolved = self.resolver.cjsResolve(id, parentPath);
                        return self.loadCjsModuleExports(cx, resolved.getPath());
                    }

                    @Override
                    public String getFunctionName() {
                        return "require";
                    }

                    @Override
                    public int getArity() {
                        return 1;
                    }

                    @Override
                    public int getLength() {
                        return 1;
                    }
                };
        if (globalScope != null) {
            requireFn.setPrototype(ScriptableObject.getFunctionPrototype(globalScope));
        }
        return requireFn;
    }

    private void installCjsHelper(Scriptable scope) {
        NodeModuleLoader self = this;
        BaseFunction helper =
                new BaseFunction() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public Object call(
                            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                        String path = Context.toString(args[0]);
                        return self.loadCjsModuleExports(cx, path);
                    }

                    @Override
                    public String getFunctionName() {
                        return "__rhinoCjsHelper__";
                    }

                    @Override
                    public int getArity() {
                        return 1;
                    }

                    @Override
                    public int getLength() {
                        return 1;
                    }
                };
        helper.setPrototype(ScriptableObject.getFunctionPrototype(scope));
        ScriptableObject.putProperty(scope, "__rhinoCjsHelper__", helper);
    }

    private final ConcurrentHashMap<String, Object> builtinStubCache = new ConcurrentHashMap<>();

    private Object getNodeBuiltinStub(Context cx, String id) {
        // Strip node: prefix
        String name = id.startsWith("node:") ? id.substring(5) : id;
        return builtinStubCache.computeIfAbsent(
                name,
                k -> {
                    String source = getBuiltinStubSource(k);
                    Script script = cx.compileString(source, "<node:" + k + ">", 1, null);
                    return script.exec(cx, globalScope, globalScope);
                });
    }

    private static final String STUBS_RESOURCE_PATH = "/org/mozilla/javascript/node/stubs/";

    static String getBuiltinStubSource(String name) {
        String resourceName = STUBS_RESOURCE_PATH + name + ".js";
        InputStream is = NodeModuleLoader.class.getResourceAsStream(resourceName);
        if (is == null) {
            return "({})";
        }
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            return "({})";
        }
    }

    private static String[] extractStubKeys(String stubSource) {
        // Simple extraction of top-level keys from "({ key: ..., key2: ... })"
        // Looks for identifiers followed by ":" at the top level of the object literal
        List<String> keys = new ArrayList<>();
        int depth = 0;
        int i = 0;
        while (i < stubSource.length()) {
            char c = stubSource.charAt(i);
            if (c == '(' || c == '{') {
                depth++;
                i++;
            } else if (c == ')' || c == '}') {
                depth--;
                i++;
            } else if (c == '\'' || c == '"') {
                // skip string
                char quote = c;
                i++;
                while (i < stubSource.length() && stubSource.charAt(i) != quote) {
                    if (stubSource.charAt(i) == '\\') i++;
                    i++;
                }
                i++; // closing quote
            } else if (depth == 2 && Character.isJavaIdentifierStart(c)) {
                // At top level of the object (depth 2 because of "({...})")
                int start = i;
                while (i < stubSource.length()
                        && Character.isJavaIdentifierPart(stubSource.charAt(i))) {
                    i++;
                }
                String word = stubSource.substring(start, i);
                // Skip whitespace
                while (i < stubSource.length() && stubSource.charAt(i) == ' ') i++;
                if (i < stubSource.length() && stubSource.charAt(i) == ':') {
                    keys.add(word);
                }
            } else {
                i++;
            }
        }
        return keys.toArray(new String[0]);
    }

    private static String escapeJsString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static boolean isValidJsIdentifier(String name) {
        if (name == null || name.isEmpty()) return false;
        if (!Character.isJavaIdentifierStart(name.charAt(0))) return false;
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) return false;
        }
        return !isReservedWord(name);
    }

    private static boolean isReservedWord(String name) {
        switch (name) {
            case "break":
            case "case":
            case "catch":
            case "continue":
            case "debugger":
            case "default":
            case "delete":
            case "do":
            case "else":
            case "finally":
            case "for":
            case "function":
            case "if":
            case "in":
            case "instanceof":
            case "new":
            case "return":
            case "switch":
            case "this":
            case "throw":
            case "try":
            case "typeof":
            case "var":
            case "void":
            case "while":
            case "with":
            case "class":
            case "const":
            case "enum":
            case "export":
            case "extends":
            case "import":
            case "super":
            case "yield":
            case "let":
            case "static":
                return true;
            default:
                return false;
        }
    }
}
