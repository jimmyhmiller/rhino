/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.node.module;

import java.net.URI;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.commonjs.module.ModuleScope;
import org.mozilla.javascript.commonjs.module.ModuleScriptProvider;
import org.mozilla.javascript.commonjs.module.Require;

/**
 * Extends {@link Require} to use the Node.js module resolution algorithm. Adds {@code
 * require.resolve} and {@code require.resolve.paths} functions. Sets up {@code __filename} and
 * {@code __dirname} in module scope.
 */
public class NodeRequire extends Require {

    private static final long serialVersionUID = 1L;
    private final NodeModuleResolver resolver;
    private final NodeFileSystem fs;

    public NodeRequire(
            Context cx,
            Scriptable nativeScope,
            ModuleScriptProvider moduleScriptProvider,
            NodeModuleResolver resolver,
            NodeFileSystem fs,
            boolean sandboxed) {
        super(cx, nativeScope, moduleScriptProvider, null, null, sandboxed);
        this.resolver = resolver;
        this.fs = fs;
        installResolve(cx, nativeScope);
    }

    @Override
    protected ResolvedId resolveModuleId(
            Context cx, Scriptable scope, Scriptable thisObj, String id) {
        String parentPath = getParentPath(thisObj);
        ResolvedModule resolved = resolver.cjsResolve(id, parentPath);
        try {
            URI uri = new URI("file", "", resolved.getPath(), null);
            return new ResolvedId(resolved.getPath(), uri, null);
        } catch (java.net.URISyntaxException e) {
            throw ScriptRuntime.throwError(cx, scope, "Invalid module path: " + resolved.getPath());
        }
    }

    private String getParentPath(Scriptable thisObj) {
        if (thisObj instanceof ModuleScope) {
            ModuleScope moduleScope = (ModuleScope) thisObj;
            URI uri = moduleScope.getUri();
            if (uri != null) {
                String path = uri.getPath();
                if (path != null) {
                    return path;
                }
            }
        }
        // Default to a synthetic entry point in the current directory
        return fs.resolve(fs.getAbsolutePath("."), "__entry__.js");
    }

    private void installResolve(Context cx, Scriptable nativeScope) {
        BaseFunction resolveFunc =
                new BaseFunction(nativeScope, ScriptableObject.getFunctionPrototype(nativeScope)) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public Object call(
                            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                        if (args == null || args.length < 1) {
                            throw ScriptRuntime.throwError(
                                    cx, scope, "require.resolve() needs one argument");
                        }
                        String id = (String) Context.jsToJava(args[0], String.class);
                        String parentPath = getCallerPath(scope);
                        ResolvedModule resolved = resolver.cjsResolve(id, parentPath);
                        return resolved.getPath();
                    }

                    @Override
                    public String getFunctionName() {
                        return "resolve";
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

        BaseFunction pathsFunc =
                new BaseFunction(nativeScope, ScriptableObject.getFunctionPrototype(nativeScope)) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public Object call(
                            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                        if (args == null || args.length < 1) {
                            throw ScriptRuntime.throwError(
                                    cx, scope, "require.resolve.paths() needs one argument");
                        }
                        String id = (String) Context.jsToJava(args[0], String.class);

                        // Relative paths don't use node_modules search
                        if (id.startsWith("./") || id.startsWith("../") || id.startsWith("/")) {
                            return null;
                        }

                        String parentPath = getCallerPath(scope);
                        String parentDir = fs.dirname(parentPath);
                        java.util.List<String> paths = resolver.nodeModulesPaths(parentDir);
                        return cx.newArray(scope, paths.toArray());
                    }

                    @Override
                    public String getFunctionName() {
                        return "paths";
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

        ScriptableObject.putProperty(resolveFunc, "paths", pathsFunc);
        ScriptableObject.putProperty(this, "resolve", resolveFunc);
    }

    private String getCallerPath(Scriptable scope) {
        while (scope != null) {
            if (scope instanceof ModuleScope) {
                URI uri = ((ModuleScope) scope).getUri();
                if (uri != null && uri.getPath() != null) {
                    return uri.getPath();
                }
            }
            scope = scope.getParentScope();
        }
        return fs.resolve(fs.getAbsolutePath("."), "__entry__.js");
    }
}
