/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

/**
 * ES2018 Async Generator implementation.
 *
 * <p>An async generator is a function declared with both `async` and `*` modifiers. Unlike regular
 * generators that return `{value, done}` directly, async generators return `Promise<{value, done}>`
 * for each step.
 *
 * <p>The key differences from ES6Generator:
 *
 * <ul>
 *   <li>Uses @@asyncIterator instead of @@iterator
 *   <li>next(), return(), throw() all return Promise&lt;{value, done}&gt;
 *   <li>Values yielded are awaited before being wrapped in the iterator result
 * </ul>
 */
public final class ES6AsyncGenerator extends ScriptableObject {
    private static final long serialVersionUID = 1L;

    static final Object ASYNC_GENERATOR_TAG = "AsyncGenerator";

    private JSFunction function;
    private Object savedState;
    private String lineSource;
    private int lineNumber;
    private State state = State.SUSPENDED_START;
    private Object delegee;

    enum State {
        SUSPENDED_START,
        SUSPENDED_YIELD,
        EXECUTING,
        COMPLETED
    }

    static ES6AsyncGenerator init(ScriptableObject scope, boolean sealed) {
        ES6AsyncGenerator prototype = new ES6AsyncGenerator();
        if (scope != null) {
            prototype.setParentScope(scope);
            prototype.setPrototype(getObjectPrototype(scope));
        }

        // Define prototype methods using LambdaFunction
        LambdaFunction next = new LambdaFunction(scope, "next", 1, ES6AsyncGenerator::js_next);
        ScriptableObject.defineProperty(prototype, "next", next, DONTENUM);

        LambdaFunction returnFunc =
                new LambdaFunction(scope, "return", 1, ES6AsyncGenerator::js_return);
        ScriptableObject.defineProperty(prototype, "return", returnFunc, DONTENUM);

        LambdaFunction throwFunc =
                new LambdaFunction(scope, "throw", 1, ES6AsyncGenerator::js_throw);
        ScriptableObject.defineProperty(prototype, "throw", throwFunc, DONTENUM);

        // Async generators use @@asyncIterator (not @@iterator)
        LambdaFunction iterator =
                new LambdaFunction(
                        scope, "[Symbol.asyncIterator]", 0, ES6AsyncGenerator::js_iterator);
        prototype.defineProperty(SymbolKey.ASYNC_ITERATOR, iterator, DONTENUM);

        prototype.defineProperty(SymbolKey.TO_STRING_TAG, "AsyncGenerator", DONTENUM | READONLY);

        if (scope != null) {
            scope.associateValue(ASYNC_GENERATOR_TAG, prototype);
        }

        return prototype;
    }

    /** Only for constructing the prototype object. */
    private ES6AsyncGenerator() {}

    public ES6AsyncGenerator(Scriptable scope, JSFunction function, Object savedState) {
        this.function = function;
        this.savedState = savedState;
        // Set parent and prototype properties
        Scriptable top = ScriptableObject.getTopLevelScope(scope);
        this.setParentScope(top);
        // Use the async generator function's .prototype property
        Object functionPrototype = ScriptableObject.getProperty(function, "prototype");
        if (functionPrototype instanceof Scriptable) {
            this.setPrototype((Scriptable) functionPrototype);
        } else {
            // Use intrinsic default prototype
            ES6AsyncGenerator prototype =
                    (ES6AsyncGenerator) ScriptableObject.getTopScopeValue(top, ASYNC_GENERATOR_TAG);
            this.setPrototype(prototype);
        }
    }

    @Override
    public String getClassName() {
        return "AsyncGenerator";
    }

    private static ES6AsyncGenerator realThis(Scriptable thisObj) {
        return LambdaConstructor.convertThisObject(thisObj, ES6AsyncGenerator.class);
    }

    private static Object js_return(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        ES6AsyncGenerator generator = realThis(thisObj);
        Object value = args.length >= 1 ? args[0] : Undefined.instance;
        return generator.asyncReturn(cx, scope, value);
    }

    private static Object js_next(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        ES6AsyncGenerator generator = realThis(thisObj);
        Object value = args.length >= 1 ? args[0] : Undefined.instance;
        return generator.asyncNext(cx, scope, value);
    }

    private static Object js_throw(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        ES6AsyncGenerator generator = realThis(thisObj);
        Object value = args.length >= 1 ? args[0] : Undefined.instance;
        return generator.asyncThrow(cx, scope, value);
    }

    private static Object js_iterator(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        return thisObj;
    }

    /** Async generator next - returns Promise<{value, done}> */
    private Object asyncNext(Context cx, Scriptable scope, Object value) {
        try {
            if (delegee != null) {
                return wrapInPromise(cx, scope, resumeDelegee(cx, scope, value));
            }
            // resumeLocal may return a Promise if it encounters yield* with async delegee
            Object result = resumeLocal(cx, scope, value);
            return wrapInPromise(cx, scope, result);
        } catch (RhinoException re) {
            return NativePromise.rejectValue(cx, scope, getErrorObject(cx, scope, re));
        }
    }

    /** Async generator return - returns Promise<{value, done}> */
    private Object asyncReturn(Context cx, Scriptable scope, Object value) {
        try {
            if (delegee != null) {
                return wrapInPromise(cx, scope, resumeDelegeeReturn(cx, scope, value));
            }
            Scriptable syncResult =
                    resumeAbruptLocal(cx, scope, NativeGenerator.GENERATOR_CLOSE, value);
            return wrapIteratorResultInPromise(cx, scope, syncResult);
        } catch (RhinoException re) {
            return NativePromise.rejectValue(cx, scope, getErrorObject(cx, scope, re));
        }
    }

    /** Async generator throw - returns Promise<{value, done}> */
    private Object asyncThrow(Context cx, Scriptable scope, Object value) {
        try {
            if (delegee != null) {
                return wrapInPromise(cx, scope, resumeDelegeeThrow(cx, scope, value));
            }
            Scriptable syncResult =
                    resumeAbruptLocal(cx, scope, NativeGenerator.GENERATOR_THROW, value);
            return wrapIteratorResultInPromise(cx, scope, syncResult);
        } catch (RhinoException re) {
            return NativePromise.rejectValue(cx, scope, getErrorObject(cx, scope, re));
        }
    }

    /** Wrap an iterator result in a Promise. The value inside the result is awaited first. */
    private Object wrapIteratorResultInPromise(Context cx, Scriptable scope, Scriptable result) {
        // Get the value from the iterator result
        Object value = ScriptableObject.getProperty(result, ES6Iterator.VALUE_PROPERTY);
        Object doneObj = ScriptableObject.getProperty(result, ES6Iterator.DONE_PROPERTY);
        Boolean done = ScriptRuntime.toBoolean(doneObj);

        // If the value is a Promise or thenable, we need to chain to it
        if (value instanceof NativePromise || isThenable(value)) {
            // Create a new promise that unwraps the value and then creates the iterator result
            return chainToValue(cx, scope, value, done);
        }

        // Value is not a thenable, just wrap the result in a resolved Promise
        return NativePromise.resolveValue(cx, scope, result);
    }

    /** Chain to a thenable value, then create the iterator result with the resolved value. */
    private Object chainToValue(Context cx, Scriptable scope, Object thenableValue, Boolean done) {
        // Get Promise.resolve to properly unwrap the thenable
        Object promiseResolved = NativePromise.resolveValue(cx, scope, thenableValue);

        if (!(promiseResolved instanceof Scriptable)) {
            // Fallback: just return the original value wrapped
            Scriptable result = ES6Iterator.makeIteratorResult(cx, scope, done);
            ScriptableObject.putProperty(result, ES6Iterator.VALUE_PROPERTY, thenableValue);
            return NativePromise.resolveValue(cx, scope, result);
        }

        // Chain with .then() to create the final iterator result
        Scriptable promiseObj = (Scriptable) promiseResolved;
        Object thenFn = ScriptableObject.getProperty(promiseObj, "then");
        if (!(thenFn instanceof Callable)) {
            // Not a proper thenable, just wrap
            Scriptable result = ES6Iterator.makeIteratorResult(cx, scope, done);
            ScriptableObject.putProperty(result, ES6Iterator.VALUE_PROPERTY, thenableValue);
            return NativePromise.resolveValue(cx, scope, result);
        }

        // Create a handler that wraps the resolved value in an iterator result
        final Boolean finalDone = done;
        LambdaFunction onFulfilled =
                new LambdaFunction(
                        scope,
                        1,
                        (Context icx, Scriptable iscope, Scriptable ithisObj, Object[] iargs) -> {
                            Object resolvedValue = iargs.length > 0 ? iargs[0] : Undefined.instance;
                            Scriptable iterResult =
                                    ES6Iterator.makeIteratorResult(icx, iscope, finalDone);
                            ScriptableObject.putProperty(
                                    iterResult, ES6Iterator.VALUE_PROPERTY, resolvedValue);
                            return iterResult;
                        });

        return ((Callable) thenFn).call(cx, scope, promiseObj, new Object[] {onFulfilled});
    }

    /**
     * Wrap a delegee result in a Promise.
     *
     * <p>If the result is already a Promise (from an async iterator like AsyncFromSyncIterator),
     * return it directly. Otherwise, treat it as a sync iterator result and wrap it.
     */
    private Object wrapInPromise(Context cx, Scriptable scope, Object result) {
        // If result is a Promise (from async iterator), return it directly
        if (result instanceof NativePromise) {
            return result;
        }
        // If result is a thenable (but not NativePromise), chain on it
        if (isThenable(result)) {
            return result;
        }
        // Otherwise treat as sync iterator result
        if (result instanceof Scriptable) {
            return wrapIteratorResultInPromise(cx, scope, (Scriptable) result);
        }
        return NativePromise.resolveValue(cx, scope, result);
    }

    private boolean isThenable(Object obj) {
        if (!(obj instanceof Scriptable)) {
            return false;
        }
        Object then = ScriptableObject.getProperty((Scriptable) obj, "then");
        return then instanceof Callable;
    }

    private static Object getErrorObject(Context cx, Scriptable scope, RhinoException re) {
        if (re instanceof JavaScriptException) {
            return ((JavaScriptException) re).getValue();
        }
        return ScriptRuntime.wrapException(re, scope, cx);
    }

    // The following methods are adapted from ES6Generator but for async generators

    private Object resumeDelegee(Context cx, Scriptable scope, Object value) {
        try {
            Object[] nextArgs = new Object[] {value};
            var nextFn = ScriptRuntime.getPropAndThis(delegee, ES6Iterator.NEXT_METHOD, cx, scope);
            Object nr = nextFn.call(cx, scope, nextArgs);

            // For async iterators (like AsyncFromSyncIterator), next() returns a Promise.
            // Since AsyncFromSyncIterator wraps a sync iterator, its Promise is immediately
            // resolved. We can extract the value synchronously to avoid the .then() chain
            // bug in Rhino's await implementation.
            if (nr instanceof NativePromise) {
                NativePromise promise = (NativePromise) nr;
                if (promise.isFulfilled()) {
                    // Promise is already resolved - extract the value synchronously
                    nr = promise.getResult();
                } else if (promise.isRejected()) {
                    // Promise is rejected - throw the rejection reason
                    Object reason = promise.getResult();
                    throw new JavaScriptException(reason, "", 0);
                }
                // If promise is still pending, fall through to return it
                // (this shouldn't happen for AsyncFromSyncIterator)
            }

            // Handle the iterator result
            if (nr instanceof Scriptable) {
                Scriptable nextResult = (Scriptable) nr;
                if (ScriptRuntime.isIteratorDone(cx, nextResult)) {
                    delegee = null;
                    return resumeLocal(
                            cx,
                            scope,
                            ScriptableObject.getProperty(nextResult, ES6Iterator.VALUE_PROPERTY));
                }
                // Not done - wrap in Promise and return
                return NativePromise.resolveValue(cx, scope, nextResult);
            }

            // Fallback: return as-is (shouldn't normally reach here)
            return nr;
        } catch (RhinoException re) {
            delegee = null;
            return resumeAbruptLocal(cx, scope, NativeGenerator.GENERATOR_THROW, re);
        }
    }

    private Object resumeDelegeeThrow(Context cx, Scriptable scope, Object value) {
        boolean returnCalled = false;
        try {
            var throwFn = ScriptRuntime.getPropAndThis(delegee, "throw", cx, scope);
            Object throwResult = throwFn.call(cx, scope, new Object[] {value});

            if (ScriptRuntime.isIteratorDone(cx, throwResult)) {
                try {
                    returnCalled = true;
                    callReturnOptionally(cx, scope, Undefined.instance);
                } finally {
                    delegee = null;
                }
                return resumeLocal(
                        cx,
                        scope,
                        ScriptRuntime.getObjectProp(
                                throwResult, ES6Iterator.VALUE_PROPERTY, cx, scope));
            }
            return ensureScriptable(throwResult);
        } catch (RhinoException re) {
            try {
                if (!returnCalled) {
                    try {
                        callReturnOptionally(cx, scope, Undefined.instance);
                    } catch (RhinoException re2) {
                        return resumeAbruptLocal(cx, scope, NativeGenerator.GENERATOR_THROW, re2);
                    }
                }
            } finally {
                delegee = null;
            }
            return resumeAbruptLocal(cx, scope, NativeGenerator.GENERATOR_THROW, re);
        }
    }

    private Object resumeDelegeeReturn(Context cx, Scriptable scope, Object value) {
        try {
            Object retResult = callReturnOptionally(cx, scope, value);
            if (retResult != null && !Undefined.isUndefined(retResult)) {
                if (ScriptRuntime.isIteratorDone(cx, retResult)) {
                    delegee = null;
                    return resumeAbruptLocal(
                            cx,
                            scope,
                            NativeGenerator.GENERATOR_CLOSE,
                            ScriptRuntime.getObjectPropNoWarn(
                                    retResult, ES6Iterator.VALUE_PROPERTY, cx, scope));
                } else {
                    return ensureScriptable(retResult);
                }
            }
            delegee = null;
            return resumeAbruptLocal(cx, scope, NativeGenerator.GENERATOR_CLOSE, value);
        } catch (RhinoException re) {
            delegee = null;
            return resumeAbruptLocal(cx, scope, NativeGenerator.GENERATOR_THROW, re);
        }
    }

    private Object resumeLocal(Context cx, Scriptable scope, Object value) {
        if (state == State.COMPLETED) {
            return ES6Iterator.makeIteratorResult(cx, scope, Boolean.TRUE);
        }
        if (state == State.EXECUTING) {
            throw ScriptRuntime.typeErrorById("msg.generator.executing");
        }

        Scriptable result = ES6Iterator.makeIteratorResult(cx, scope, Boolean.FALSE);
        state = State.EXECUTING;

        try {
            Object r =
                    function.resumeGenerator(
                            cx, scope, NativeGenerator.GENERATOR_SEND, savedState, value);

            if (r instanceof ES6Generator.YieldStarResult) {
                state = State.SUSPENDED_YIELD;
                ES6Generator.YieldStarResult ysResult = (ES6Generator.YieldStarResult) r;
                try {
                    // For async generators, we should try to get an async iterator first
                    delegee = getAsyncOrSyncIterator(cx, scope, ysResult.getResult());
                } catch (RhinoException re) {
                    return resumeAbruptLocal(cx, scope, NativeGenerator.GENERATOR_THROW, re);
                }

                Object delResult;
                try {
                    delResult = resumeDelegee(cx, scope, Undefined.instance);
                } finally {
                    state = State.EXECUTING;
                }
                // If delegee is async (returns Promise), return it - the Promise handling
                // will complete the delegation when the Promise resolves
                if (delResult instanceof NativePromise || isThenable(delResult)) {
                    // The Promise from the async delegee will handle done checking
                    return ScriptableObject.ensureScriptable(delResult);
                }
                Scriptable syncResult = ScriptableObject.ensureScriptable(delResult);
                if (ScriptRuntime.isIteratorDone(cx, syncResult)) {
                    state = State.COMPLETED;
                }
                return syncResult;
            }

            ScriptableObject.putProperty(result, ES6Iterator.VALUE_PROPERTY, r);

        } catch (NativeGenerator.GeneratorClosedException gce) {
            state = State.COMPLETED;
        } catch (JavaScriptException jse) {
            state = State.COMPLETED;
            if (jse.getValue() instanceof NativeIterator.StopIteration) {
                ScriptableObject.putProperty(
                        result,
                        ES6Iterator.VALUE_PROPERTY,
                        ((NativeIterator.StopIteration) jse.getValue()).getValue());
            } else {
                lineNumber = jse.lineNumber();
                lineSource = jse.lineSource();
                if (jse.getValue() instanceof RhinoException) {
                    throw (RhinoException) jse.getValue();
                }
                throw jse;
            }
        } catch (RhinoException re) {
            state = State.COMPLETED;
            lineNumber = re.lineNumber();
            lineSource = re.lineSource();
            throw re;
        } finally {
            if (state == State.COMPLETED) {
                ScriptableObject.putProperty(result, ES6Iterator.DONE_PROPERTY, Boolean.TRUE);
            } else {
                state = State.SUSPENDED_YIELD;
            }
        }
        return result;
    }

    /**
     * For yield* in async generators, try to get @@asyncIterator first, then fall back
     * to @@iterator wrapped in AsyncFromSyncIterator.
     *
     * <p>Per ES2018 25.5.3.2 step 5.b: - If the object has @@asyncIterator, use it directly -
     * Otherwise, get @@iterator and wrap in CreateAsyncFromSyncIterator
     */
    private Object getAsyncOrSyncIterator(Context cx, Scriptable scope, Object obj) {
        Scriptable sObj = ScriptableObject.ensureScriptable(obj);
        // First try @@asyncIterator
        Object asyncIterMethod = ScriptableObject.getProperty(sObj, SymbolKey.ASYNC_ITERATOR);
        if (asyncIterMethod instanceof Callable) {
            return ((Callable) asyncIterMethod).call(cx, scope, sObj, ScriptRuntime.emptyArgs);
        }
        // Fall back to @@iterator, wrapped in AsyncFromSyncIterator
        Object syncIterator = ScriptRuntime.callIterator(obj, cx, scope);
        return new AsyncFromSyncIterator(scope, syncIterator);
    }

    private Scriptable resumeAbruptLocal(Context cx, Scriptable scope, int op, Object value) {
        if (state == State.EXECUTING) {
            throw ScriptRuntime.typeErrorById("msg.generator.executing");
        }
        if (state == State.SUSPENDED_START) {
            state = State.COMPLETED;
        }

        Scriptable result = ES6Iterator.makeIteratorResult(cx, scope, Boolean.FALSE);
        if (state == State.COMPLETED) {
            if (op == NativeGenerator.GENERATOR_THROW) {
                throw new JavaScriptException(value, lineSource, lineNumber);
            }
            ScriptableObject.putProperty(result, ES6Iterator.VALUE_PROPERTY, value);
            ScriptableObject.putProperty(result, ES6Iterator.DONE_PROPERTY, Boolean.TRUE);
            return result;
        }

        state = State.EXECUTING;

        Object throwValue = value;
        if (op == NativeGenerator.GENERATOR_CLOSE) {
            if (!(value instanceof NativeGenerator.GeneratorClosedException)) {
                throwValue = new NativeGenerator.GeneratorClosedException(value);
            }
        } else {
            if (value instanceof JavaScriptException) {
                throwValue = ((JavaScriptException) value).getValue();
            } else if (value instanceof RhinoException) {
                throwValue = ScriptRuntime.wrapException((Throwable) value, scope, cx);
            }
        }

        try {
            Object r = function.resumeGenerator(cx, scope, op, savedState, throwValue);
            ScriptableObject.putProperty(result, ES6Iterator.VALUE_PROPERTY, r);
            state = State.SUSPENDED_YIELD;
        } catch (NativeGenerator.GeneratorClosedException gce) {
            state = State.COMPLETED;
            ScriptableObject.putProperty(result, ES6Iterator.VALUE_PROPERTY, gce.getValue());
        } catch (JavaScriptException jse) {
            state = State.COMPLETED;
            if (jse.getValue() instanceof NativeIterator.StopIteration) {
                ScriptableObject.putProperty(
                        result,
                        ES6Iterator.VALUE_PROPERTY,
                        ((NativeIterator.StopIteration) jse.getValue()).getValue());
            } else {
                lineNumber = jse.lineNumber();
                lineSource = jse.lineSource();
                if (jse.getValue() instanceof RhinoException) {
                    throw (RhinoException) jse.getValue();
                }
                throw jse;
            }
        } catch (RhinoException re) {
            state = State.COMPLETED;
            lineNumber = re.lineNumber();
            lineSource = re.lineSource();
            throw re;
        } finally {
            if (state == State.COMPLETED) {
                delegee = null;
                ScriptableObject.putProperty(result, ES6Iterator.DONE_PROPERTY, Boolean.TRUE);
            }
        }
        return result;
    }

    private Object callReturnOptionally(Context cx, Scriptable scope, Object value) {
        Object[] retArgs =
                Undefined.isUndefined(value) ? ScriptRuntime.emptyArgs : new Object[] {value};
        Object retFnObj =
                ScriptRuntime.getObjectPropNoWarn(delegee, ES6Iterator.RETURN_METHOD, cx, scope);
        if (retFnObj != null && !Undefined.isUndefined(retFnObj)) {
            if (!(retFnObj instanceof Callable)) {
                throw ScriptRuntime.typeErrorById(
                        "msg.isnt.function",
                        ES6Iterator.RETURN_METHOD,
                        ScriptRuntime.typeof(retFnObj));
            }
            return ((Callable) retFnObj).call(cx, scope, ensureScriptable(delegee), retArgs);
        }
        return null;
    }
}
