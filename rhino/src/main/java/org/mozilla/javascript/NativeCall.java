/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

/**
 * This class implements the activation object.
 *
 * <p>See ECMA 10.1.6
 *
 * @see org.mozilla.javascript.Arguments
 * @author Norris Boyd
 */
public final class NativeCall extends IdScriptableObject {
    private static final long serialVersionUID = -7471457301304454454L;

    private static final Object CALL_TAG = "Call";

    static void init(Scriptable scope, boolean sealed) {
        NativeCall obj = new NativeCall();
        obj.exportAsJSClass(MAX_PROTOTYPE_ID, scope, sealed);
    }

    NativeCall() {
        function = null;
        originalArgs = null;
        isStrict = false;
    }

    NativeCall(
            JSFunction function,
            Context cx,
            Scriptable scope,
            Object[] args,
            boolean isArrow,
            boolean isStrict,
            boolean argsHasRest,
            boolean requiresArgumentObject) {
        this.function = function;

        setParentScope(scope);
        // leave prototype null

        this.originalArgs = (args == null) ? ScriptRuntime.emptyArgs : args;
        this.isStrict = isStrict;

        // initialize values of arguments
        int paramAndVarCount = function.getParamAndVarCount();
        int paramCount = function.getParamCount();
        if (paramAndVarCount != 0) {
            if (argsHasRest) {
                Object[] vals;
                if (args.length >= paramCount) {
                    vals = new Object[args.length - paramCount];
                    System.arraycopy(args, paramCount, vals, 0, args.length - paramCount);
                } else {
                    vals = ScriptRuntime.emptyArgs;
                }

                for (int i = 0; i < paramCount; ++i) {
                    String name = function.getParamOrVarName(i);
                    Object val = i < args.length ? args[i] : Undefined.instance;
                    defineProperty(name, val, PERMANENT);
                }
                defineProperty(
                        function.getParamOrVarName(paramCount),
                        cx.newArray(scope, vals),
                        PERMANENT);
            } else {
                for (int i = 0; i < paramCount; ++i) {
                    String name = function.getParamOrVarName(i);
                    Object val = i < args.length ? args[i] : Undefined.instance;
                    defineProperty(name, val, PERMANENT);
                }
            }
        }

        // initialize "arguments" property but only if it was not overridden by
        // the parameter with the same name
        if (requiresArgumentObject && !isArrow && !super.has("arguments", this)) {
            defineProperty("arguments", new Arguments(this, cx), PERMANENT);
        }

        // Determine the NFE (named function expression) binding name, if any.
        // Per spec, this creates an immutable binding inside the function body.
        String nfeName = null;
        if (function.getDescriptor().declaredAsFunctionExpression()) {
            String fn = function.getFunctionName();
            if (fn != null && !fn.isEmpty()) {
                nfeName = fn;
            }
        }

        if (paramAndVarCount != 0) {
            for (int i = paramCount; i < paramAndVarCount; ++i) {
                String name = function.getParamOrVarName(i);
                if (!super.has(name, this)) {
                    if (function.getParamOrVarConst(i)) {
                        // Initialize const to TDZ_VALUE with proper const flags
                        // UNINITIALIZED_CONST allows first assignment, then becomes READONLY
                        // CONST_BINDING ensures reassignment throws in ES6 non-strict mode
                        defineProperty(
                                name,
                                Undefined.TDZ_VALUE,
                                PERMANENT | READONLY | UNINITIALIZED_CONST | CONST_BINDING);
                    } else if (function.getParamOrVarLetOrConst(i)) {
                        // Initialize let variables to TDZ_VALUE for temporal dead zone
                        // Skip TDZ for internal temp variables (used by destructuring)
                        if (name.startsWith("$")) {
                            defineProperty(name, Undefined.instance, PERMANENT);
                        } else {
                            defineProperty(name, Undefined.TDZ_VALUE, PERMANENT);
                        }
                    } else if (name.equals(nfeName)) {
                        // Named function expression: immutable binding.
                        // UNINITIALIZED_CONST allows the initial SETNAME(THISFN),
                        // then READONLY without CONST_BINDING gives correct behavior:
                        //   non-strict: silently ignores reassignment
                        //   strict: throws TypeError
                        defineProperty(
                                name,
                                Undefined.instance,
                                PERMANENT | READONLY | UNINITIALIZED_CONST);
                    } else if (function.hasFunctionNamed(name)) {
                        defineProperty(name, Undefined.instance, PERMANENT);
                    } else {
                        // Regular var - define but don't initialize
                        defineProperty(name, Undefined.instance, PERMANENT);
                    }
                }
            }
        }
    }

    @Override
    public String getClassName() {
        return "Call";
    }

    @Override
    protected int findPrototypeId(String s) {
        return "constructor".equals(s) ? Id_constructor : 0;
    }

    @Override
    protected void initPrototypeId(int id) {
        String s;
        int arity;
        if (id == Id_constructor) {
            arity = 1;
            s = "constructor";
        } else {
            throw new IllegalArgumentException(String.valueOf(id));
        }
        initPrototypeMethod(CALL_TAG, id, s, arity);
    }

    @Override
    public Object execIdCall(
            IdFunctionObject f, Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        if (!f.hasTag(CALL_TAG)) {
            return super.execIdCall(f, cx, scope, thisObj, args);
        }
        int id = f.methodId();
        if (id == Id_constructor) {
            if (thisObj != null) {
                throw Context.reportRuntimeErrorById("msg.only.from.new", "Call");
            }
            ScriptRuntime.checkDeprecated(cx, "Call");
            NativeCall result = new NativeCall();
            result.setPrototype(getObjectPrototype(scope));
            return result;
        }
        throw new IllegalArgumentException(String.valueOf(id));
    }

    public Scriptable getHomeObject() {
        return function.getHomeObject();
    }

    private static final int Id_constructor = 1, MAX_PROTOTYPE_ID = 1;

    final JSFunction function;
    final Object[] originalArgs;
    final boolean isStrict;

    transient NativeCall parentActivationCall;
}
