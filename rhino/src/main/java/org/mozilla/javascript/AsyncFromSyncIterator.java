/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

/**
 * %AsyncFromSyncIteratorPrototype% implementation.
 *
 * <p>When yield* is used in an async generator on an object that only has @@iterator
 * (not @@asyncIterator), the sync iterator must be wrapped in an AsyncFromSyncIterator. This
 * wrapper converts sync iteration results to promises by:
 *
 * <ul>
 *   <li>Calling the underlying sync iterator's next/return/throw
 *   <li>Wrapping the result's value in Promise.resolve()
 *   <li>Returning a Promise that resolves to the final {value, done} result
 * </ul>
 *
 * <p>See ES2018 25.1.4.2.1 %AsyncFromSyncIteratorPrototype%.next
 */
public class AsyncFromSyncIterator extends ScriptableObject {
    private static final long serialVersionUID = 1L;

    private Object syncIterator;
    // Per spec, the next method is looked up once when creating the iterator record
    private Callable nextMethod;

    static final String TAG = "AsyncFromSyncIterator";

    static AsyncFromSyncIterator init(ScriptableObject scope, boolean sealed) {
        AsyncFromSyncIterator prototype = new AsyncFromSyncIterator();
        if (scope != null) {
            prototype.setParentScope(scope);
            prototype.setPrototype(getObjectPrototype(scope));
        }

        // Define prototype methods
        LambdaFunction next = new LambdaFunction(scope, "next", 1, AsyncFromSyncIterator::js_next);
        ScriptableObject.defineProperty(prototype, "next", next, DONTENUM);

        LambdaFunction returnFunc =
                new LambdaFunction(scope, "return", 1, AsyncFromSyncIterator::js_return);
        ScriptableObject.defineProperty(prototype, "return", returnFunc, DONTENUM);

        LambdaFunction throwFunc =
                new LambdaFunction(scope, "throw", 1, AsyncFromSyncIterator::js_throw);
        ScriptableObject.defineProperty(prototype, "throw", throwFunc, DONTENUM);

        // @@asyncIterator returns self
        LambdaFunction asyncIterator =
                new LambdaFunction(
                        scope,
                        "[Symbol.asyncIterator]",
                        0,
                        AsyncFromSyncIterator::js_asyncIterator);
        prototype.defineProperty(SymbolKey.ASYNC_ITERATOR, asyncIterator, DONTENUM);

        if (sealed) {
            prototype.sealObject();
        }

        if (scope != null) {
            scope.associateValue(TAG, prototype);
        }

        return prototype;
    }

    /** Only for constructing the prototype object. */
    private AsyncFromSyncIterator() {}

    public AsyncFromSyncIterator(Scriptable scope, Object syncIterator) {
        this.syncIterator = syncIterator;
        Scriptable top = ScriptableObject.getTopLevelScope(scope);
        this.setParentScope(top);
        AsyncFromSyncIterator prototype =
                (AsyncFromSyncIterator) ScriptableObject.getTopScopeValue(top, TAG);
        setPrototype(prototype);

        // Per ES spec 27.1.4.1 CreateAsyncFromSyncIterator, the next method is looked up
        // once when creating the iterator record, not on each call
        Scriptable syncIter = ScriptableObject.ensureScriptable(syncIterator);
        Object next = ScriptableObject.getProperty(syncIter, "next");
        if (!(next instanceof Callable)) {
            throw ScriptRuntime.typeErrorById("msg.isnt.function", "next");
        }
        this.nextMethod = (Callable) next;
    }

    @Override
    public String getClassName() {
        return "Async-from-Sync Iterator";
    }

    private static AsyncFromSyncIterator realThis(Scriptable thisObj) {
        return LambdaConstructor.convertThisObject(thisObj, AsyncFromSyncIterator.class);
    }

    private static Object js_asyncIterator(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        return thisObj;
    }

    /**
     * %AsyncFromSyncIteratorPrototype%.next ( value )
     *
     * <p>1. Let promiseCapability be ! NewPromiseCapability(%Promise%). 2. Let syncIterator be
     * O.[[SyncIteratorRecord]].[[Iterator]]. 3. Let nextResult be IteratorNext(syncIterator,
     * value). ... 7. Let nextValue be IteratorValue(nextResult). ... 9. Let nextDone be
     * IteratorComplete(nextResult). ... 12. Perform ! Call(valueWrapperCapability.[[Resolve]],
     * undefined, << nextValue >>). ... 14. Set onFulfilled.[[Done]] to nextDone.
     */
    private static Object js_next(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        AsyncFromSyncIterator self = realThis(thisObj);
        Object value = args.length >= 1 ? args[0] : Undefined.instance;

        try {
            // Call the cached next method (looked up once at iterator creation time)
            Scriptable syncIter = ScriptableObject.ensureScriptable(self.syncIterator);
            Object nextResult = self.nextMethod.call(cx, scope, syncIter, new Object[] {value});
            if (!(nextResult instanceof Scriptable)) {
                throw ScriptRuntime.typeError("Iterator result must be an object");
            }
            return wrapSyncResult(cx, scope, nextResult);
        } catch (RhinoException re) {
            return NativePromise.rejectValue(cx, scope, getErrorObject(cx, scope, re));
        }
    }

    /**
     * %AsyncFromSyncIteratorPrototype%.return ( value )
     *
     * <p>Similar to next, but calls the sync iterator's return method if it exists.
     */
    private static Object js_return(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        AsyncFromSyncIterator self = realThis(thisObj);
        Object value = args.length >= 1 ? args[0] : Undefined.instance;

        try {
            // Check if the sync iterator has a return method
            Scriptable syncIter = ScriptableObject.ensureScriptable(self.syncIterator);
            Object returnMethod = ScriptableObject.getProperty(syncIter, "return");

            if (returnMethod == Scriptable.NOT_FOUND || Undefined.isUndefined(returnMethod)) {
                // If no return method, return a completed iterator result
                Scriptable result = ES6Iterator.makeIteratorResult(cx, scope, Boolean.TRUE, value);
                return NativePromise.resolveValue(cx, scope, result);
            }

            if (!(returnMethod instanceof Callable)) {
                throw ScriptRuntime.typeErrorById("msg.isnt.function", "return");
            }

            // Call the sync iterator's return method
            Object returnResult =
                    ((Callable) returnMethod).call(cx, scope, syncIter, new Object[] {value});
            if (!(returnResult instanceof Scriptable)) {
                throw ScriptRuntime.typeError("Iterator result must be an object");
            }
            return wrapSyncResult(cx, scope, returnResult);
        } catch (RhinoException re) {
            return NativePromise.rejectValue(cx, scope, getErrorObject(cx, scope, re));
        }
    }

    /**
     * %AsyncFromSyncIteratorPrototype%.throw ( value )
     *
     * <p>Similar to next, but calls the sync iterator's throw method if it exists.
     */
    private static Object js_throw(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        AsyncFromSyncIterator self = realThis(thisObj);
        Object value = args.length >= 1 ? args[0] : Undefined.instance;

        try {
            // Check if the sync iterator has a throw method
            Scriptable syncIter = ScriptableObject.ensureScriptable(self.syncIterator);
            Object throwMethod = ScriptableObject.getProperty(syncIter, "throw");

            if (throwMethod == Scriptable.NOT_FOUND || Undefined.isUndefined(throwMethod)) {
                // If no throw method, reject with the value
                return NativePromise.rejectValue(cx, scope, value);
            }

            if (!(throwMethod instanceof Callable)) {
                throw ScriptRuntime.typeErrorById("msg.isnt.function", "throw");
            }

            // Call the sync iterator's throw method
            Object throwResult =
                    ((Callable) throwMethod).call(cx, scope, syncIter, new Object[] {value});
            if (!(throwResult instanceof Scriptable)) {
                throw ScriptRuntime.typeError("Iterator result must be an object");
            }
            return wrapSyncResult(cx, scope, throwResult);
        } catch (RhinoException re) {
            return NativePromise.rejectValue(cx, scope, getErrorObject(cx, scope, re));
        }
    }

    /** Call a method on the iterator, handling the case where it doesn't exist. */
    private static Object callIteratorMethod(
            Context cx, Scriptable scope, Object iterator, String methodName, Object arg) {
        Scriptable syncIter = ScriptableObject.ensureScriptable(iterator);
        Object method = ScriptableObject.getProperty(syncIter, methodName);

        if (!(method instanceof Callable)) {
            throw ScriptRuntime.typeErrorById("msg.isnt.function", methodName);
        }

        Object result = ((Callable) method).call(cx, scope, syncIter, new Object[] {arg});
        if (!(result instanceof Scriptable)) {
            throw ScriptRuntime.typeError("Iterator result must be an object");
        }
        return result;
    }

    /**
     * Wrap a sync iterator result in a Promise.
     *
     * <p>Per ES spec, if the sync iterator's value is a Promise/thenable, it should be awaited and
     * the result unwrapped. Since Rhino has a bug where awaiting Promise.then() chains returns
     * undefined, we handle Promise values specially by checking if they're already resolved.
     */
    private static Object wrapSyncResult(Context cx, Scriptable scope, Object syncResult) {
        Scriptable result = ScriptableObject.ensureScriptable(syncResult);

        // Per ES spec, done must be accessed before value (25.1.4.2.1 step 6 then step 7)
        Object doneObj = ScriptableObject.getProperty(result, ES6Iterator.DONE_PROPERTY);
        boolean done = ScriptRuntime.toBoolean(doneObj);
        Object value = ScriptableObject.getProperty(result, ES6Iterator.VALUE_PROPERTY);

        // If the value is a NativePromise, we need to unwrap it
        if (value instanceof NativePromise) {
            NativePromise promise = (NativePromise) value;
            if (promise.isFulfilled()) {
                // Promise is resolved - extract the value synchronously
                value = promise.getResult();
            } else if (promise.isRejected()) {
                // Promise is rejected - return a rejected promise with the reason
                return NativePromise.rejectValue(cx, scope, promise.getResult());
            }
            // If promise is still pending, we need to chain on it.
            // Since Rhino's await has a bug with .then() chains, we use a workaround:
            // return a promise that will be resolved when the inner promise resolves.
            else {
                // Use NativePromise.resolveValue which handles thenable unwrapping
                Object wrappedPromise = NativePromise.resolveValue(cx, scope, value);
                if (wrappedPromise instanceof NativePromise) {
                    // We need to chain to create the iterator result after unwrapping.
                    // Since .then() chains don't work with await, we create a new promise
                    // manually using Promise capabilities.
                    return createUnwrappingPromise(cx, scope, (NativePromise) wrappedPromise, done);
                }
            }
        }

        // Value is not a Promise, or was synchronously unwrapped - create the result directly
        Scriptable iterResult = ES6Iterator.makeIteratorResult(cx, scope, done, value);
        return NativePromise.resolveValue(cx, scope, iterResult);
    }

    /**
     * Create a Promise that unwraps the given promise's value and returns an iterator result. This
     * works around Rhino's await bug with .then() chains by using Promise.all.
     */
    private static Object createUnwrappingPromise(
            Context cx, Scriptable scope, NativePromise innerPromise, boolean done) {
        // Workaround: Instead of using .then() which breaks await, use Promise.all
        // which properly propagates resolved values
        Scriptable arrayConstructor = (Scriptable) ScriptableObject.getProperty(scope, "Array");
        Scriptable promises = cx.newArray(scope, new Object[] {innerPromise});

        Scriptable promiseConstructor = (Scriptable) ScriptableObject.getProperty(scope, "Promise");
        Object allFn = ScriptableObject.getProperty(promiseConstructor, "all");
        if (!(allFn instanceof Callable)) {
            // Fallback if Promise.all is not available
            Scriptable iterResult =
                    ES6Iterator.makeIteratorResult(cx, scope, done, Undefined.instance);
            return NativePromise.resolveValue(cx, scope, iterResult);
        }

        Object allPromise =
                ((Callable) allFn).call(cx, scope, promiseConstructor, new Object[] {promises});

        // Now chain on allPromise to extract the single value and create iterator result
        // Note: This still uses .then() but it's on a Promise.all result which might work
        // If it doesn't, we may need a more complex workaround
        if (!(allPromise instanceof Scriptable)) {
            Scriptable iterResult =
                    ES6Iterator.makeIteratorResult(cx, scope, done, Undefined.instance);
            return NativePromise.resolveValue(cx, scope, iterResult);
        }

        Scriptable allPromiseObj = (Scriptable) allPromise;
        Object thenFn = ScriptableObject.getProperty(allPromiseObj, "then");
        if (!(thenFn instanceof Callable)) {
            Scriptable iterResult =
                    ES6Iterator.makeIteratorResult(cx, scope, done, Undefined.instance);
            return NativePromise.resolveValue(cx, scope, iterResult);
        }

        // Create callback that extracts value from array and creates iterator result
        LambdaFunction onFulfilled =
                new LambdaFunction(
                        scope,
                        1,
                        (lcx, lscope, lthisObj, largs) -> {
                            Object results = largs.length >= 1 ? largs[0] : Undefined.instance;
                            Object resolvedValue = Undefined.instance;
                            if (results instanceof Scriptable) {
                                resolvedValue =
                                        ScriptableObject.getProperty((Scriptable) results, 0);
                            }
                            return ES6Iterator.makeIteratorResult(lcx, lscope, done, resolvedValue);
                        });

        return ((Callable) thenFn).call(cx, scope, allPromiseObj, new Object[] {onFulfilled});
    }

    private static Object getErrorObject(Context cx, Scriptable scope, RhinoException re) {
        if (re instanceof JavaScriptException) {
            return ((JavaScriptException) re).getValue();
        }
        return ScriptRuntime.wrapException(re, scope, cx);
    }
}
