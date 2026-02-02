/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

/**
 * The class for results of the Function.bind() operation EcmaScript 5 spec, 15.3.4.5
 *
 * @author Raphael Speyer
 */
public class BoundFunction extends BaseFunction {

    private static final long serialVersionUID = 2118137342826470729L;

    private final Callable targetFunction;
    private final Scriptable boundThis;
    private final Object[] boundArgs;
    private final int length;
    private final String boundName;

    public BoundFunction(
            Context cx,
            Scriptable scope,
            Callable targetFunction,
            Scriptable boundThis,
            Object[] boundArgs) {
        this.targetFunction = targetFunction;
        this.boundThis = boundThis;
        this.boundArgs = boundArgs;
        if (targetFunction instanceof BaseFunction) {
            length = Math.max(0, ((BaseFunction) targetFunction).getLength() - boundArgs.length);
        } else {
            length = 0;
        }

        // Per spec: Get targetName from Target's "name" property
        // 12. Let targetName be Get(Target, "name").
        // 14. If Type(targetName) is not String, let targetName be the empty string.
        // 15. Perform SetFunctionName(F, targetName, "bound").
        String targetName = "";
        if (targetFunction instanceof Scriptable) {
            Object nameVal = ScriptableObject.getProperty((Scriptable) targetFunction, "name");
            if (nameVal instanceof CharSequence) {
                targetName = nameVal.toString();
            }
        }
        this.boundName = "bound " + targetName;

        ScriptRuntime.setFunctionProtoAndParent(this, cx, scope, false);
        // In ES6+ mode, bound functions inherit "caller" and "arguments" throwing accessors
        // from Function.prototype. In pre-ES6 mode, we need to define our own throwing
        // accessors since Function.prototype doesn't have them.
        if (cx.getLanguageVersion() < Context.VERSION_ES6) {
            Function thrower = ScriptRuntime.typeErrorThrower(cx);
            var throwing = new DescriptorInfo(false, NOT_FOUND, false, thrower, thrower, NOT_FOUND);
            this.defineOwnProperty(cx, "caller", throwing, false);
            this.defineOwnProperty(cx, "arguments", throwing, false);
        }
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] extraArgs) {
        return targetFunction.call(cx, scope, getCallThis(cx, scope), concat(boundArgs, extraArgs));
    }

    @Override
    public Scriptable construct(Context cx, Scriptable scope, Object[] extraArgs) {
        if (targetFunction instanceof Constructable) {
            return ((Constructable) targetFunction)
                    .construct(cx, scope, concat(boundArgs, extraArgs));
        }
        throw ScriptRuntime.typeErrorById("msg.not.ctor");
    }

    @Override
    public boolean isConstructor() {
        // Per ES6 spec, a bound function has [[Construct]] internal method
        // only if the target function has [[Construct]].
        // Arrow functions don't have [[Construct]], so binding an arrow
        // function should result in a non-constructor.
        if (targetFunction instanceof Function) {
            return ((Function) targetFunction).isConstructor();
        }
        // Non-Function Callable objects are not constructors
        return false;
    }

    @Override
    public boolean hasInstance(Scriptable instance) {
        if (targetFunction instanceof Function) {
            return ((Function) targetFunction).hasInstance(instance);
        }
        throw ScriptRuntime.typeErrorById("msg.not.ctor");
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    protected boolean includeNonStandardProps() {
        // Bound functions should NOT have own "caller" and "arguments" properties.
        // They define their own throwing accessors in the constructor.
        return false;
    }

    @Override
    public String getFunctionName() {
        return boundName;
    }

    private static Object[] concat(Object[] first, Object[] second) {
        Object[] args = new Object[first.length + second.length];
        System.arraycopy(first, 0, args, 0, first.length);
        System.arraycopy(second, 0, args, first.length, second.length);
        return args;
    }

    Callable getTargetFunction() {
        return targetFunction;
    }

    Object[] getBoundArgs() {
        return boundArgs;
    }

    Scriptable getCallThis(Context cx, Scriptable scope) {
        Scriptable callThis = boundThis;
        if (callThis == null && ScriptRuntime.hasTopCall(cx)) {
            callThis = ScriptRuntime.getTopCallScope(cx);
        }
        if (callThis == null) {
            callThis = getTopLevelScope(scope);
        }
        return callThis;
    }

    static boolean equalObjectGraphs(BoundFunction f1, BoundFunction f2, EqualObjectGraphs eq) {
        return eq.equalGraphs(f1.boundThis, f2.boundThis)
                && eq.equalGraphs(f1.targetFunction, f2.targetFunction)
                && eq.equalGraphs(f1.boundArgs, f2.boundArgs);
    }
}
