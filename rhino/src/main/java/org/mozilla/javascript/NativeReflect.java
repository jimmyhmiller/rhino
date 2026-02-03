/* -*- Mode: java; tab-width: 4; indent-tabs-mode: 1; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.ArrayList;
import java.util.List;
import org.mozilla.javascript.ScriptRuntime.StringIdOrIndex;

/**
 * This class implements the Reflect object.
 *
 * @author Ronald Brill
 */
final class NativeReflect extends ScriptableObject {
    private static final long serialVersionUID = 2920773905356325445L;

    private static final String REFLECT_TAG = "Reflect";

    public static Object init(Context cx, Scriptable scope, boolean sealed) {
        NativeReflect reflect = new NativeReflect();
        reflect.setPrototype(getObjectPrototype(scope));
        reflect.setParentScope(scope);

        reflect.defineBuiltinProperty(scope, "apply", 3, NativeReflect::apply);
        reflect.defineBuiltinProperty(scope, "construct", 2, NativeReflect::construct);
        reflect.defineBuiltinProperty(scope, "defineProperty", 3, NativeReflect::defineProperty);
        reflect.defineBuiltinProperty(scope, "deleteProperty", 2, NativeReflect::deleteProperty);
        reflect.defineBuiltinProperty(scope, "get", 2, NativeReflect::get);
        reflect.defineBuiltinProperty(
                scope, "getOwnPropertyDescriptor", 2, NativeReflect::getOwnPropertyDescriptor);
        reflect.defineBuiltinProperty(scope, "getPrototypeOf", 1, NativeReflect::getPrototypeOf);
        reflect.defineBuiltinProperty(scope, "has", 2, NativeReflect::has);
        reflect.defineBuiltinProperty(scope, "isExtensible", 1, NativeReflect::isExtensible);
        reflect.defineBuiltinProperty(scope, "ownKeys", 1, NativeReflect::ownKeys);
        reflect.defineBuiltinProperty(
                scope, "preventExtensions", 1, NativeReflect::preventExtensions);
        reflect.defineBuiltinProperty(scope, "set", 3, NativeReflect::set);
        reflect.defineBuiltinProperty(scope, "setPrototypeOf", 2, NativeReflect::setPrototypeOf);

        reflect.defineProperty(SymbolKey.TO_STRING_TAG, REFLECT_TAG, DONTENUM | READONLY);
        if (sealed) {
            reflect.sealObject();
        }
        return reflect;
    }

    private NativeReflect() {}

    @Override
    public String getClassName() {
        return "Reflect";
    }

    private static Object apply(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        if (args.length < 3) {
            throw ScriptRuntime.typeErrorById(
                    "msg.method.missing.parameter",
                    "Reflect.apply",
                    "3",
                    Integer.toString(args.length));
        }

        Scriptable callable = ScriptableObject.ensureScriptable(args[0]);

        if (args[1] instanceof Scriptable) {
            thisObj = (Scriptable) args[1];
        } else if (ScriptRuntime.isPrimitive(args[1])) {
            thisObj = cx.newObject(scope, "Object", new Object[] {args[1]});
        }

        if (ScriptRuntime.isSymbol(args[2])) {
            throw ScriptRuntime.typeErrorById("msg.arg.not.object", ScriptRuntime.typeof(args[2]));
        }
        ScriptableObject argumentsList = ScriptableObject.ensureScriptableObject(args[2]);

        return ScriptRuntime.applyOrCall(
                true, cx, scope, callable, new Object[] {thisObj, argumentsList});
    }

    /**
     * see <a href="https://262.ecma-international.org/12.0/#sec-reflect.construct">28.1.2
     * Reflect.construct (target, argumentsList[, newTarget])</a>
     */
    private static Scriptable construct(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        /*
         * 1. If IsConstructor(target) is false, throw a TypeError exception.
         * 2. If newTarget is not present, set newTarget to target.
         * 3. Else if IsConstructor(newTarget) is false, throw a TypeError exception.
         * 4. Let args be ? CreateListFromArrayLike(argumentsList).
         * 5. Return ? Construct(target, args, newTarget).
         */
        if (args.length < 1) {
            throw ScriptRuntime.typeErrorById(
                    "msg.method.missing.parameter",
                    "Reflect.construct",
                    "3",
                    Integer.toString(args.length));
        }

        if (!AbstractEcmaObjectOperations.isConstructor(cx, args[0])) {
            throw ScriptRuntime.typeErrorById("msg.not.ctor", ScriptRuntime.typeof(args[0]));
        }

        Constructable ctor = (Constructable) args[0];
        if (args.length < 2) {
            return ctor.construct(cx, scope, ScriptRuntime.emptyArgs);
        }

        if (args.length > 2 && !AbstractEcmaObjectOperations.isConstructor(cx, args[2])) {
            throw ScriptRuntime.typeErrorById("msg.not.ctor", ScriptRuntime.typeof(args[2]));
        }

        Object[] callArgs = ScriptRuntime.getApplyArguments(cx, args[1]);

        Object newTargetPrototype = null;
        if (args.length > 2) {
            Scriptable newTarget = ScriptableObject.ensureScriptable(args[2]);

            if (newTarget instanceof BaseFunction) {
                newTargetPrototype = ((BaseFunction) newTarget).getPrototypeProperty();
            } else {
                newTargetPrototype = newTarget.get("prototype", newTarget);
            }

            if (!(newTargetPrototype instanceof Scriptable)
                    || ScriptRuntime.isSymbol(newTargetPrototype)
                    || Undefined.isUndefined(newTargetPrototype)) {
                newTargetPrototype = null;
            }
        }

        // our Constructable interface does not support the newTarget;
        // therefore we use a cloned implementation that fixes
        // the prototype before executing call(..).
        if (ctor instanceof BaseFunction && newTargetPrototype != null) {
            BaseFunction ctorBaseFunction = (BaseFunction) ctor;
            Scriptable result = ctorBaseFunction.createObject(cx, scope);
            if (result != null) {
                result.setPrototype((Scriptable) newTargetPrototype);

                Object val = ctorBaseFunction.call(cx, scope, result, callArgs);
                if (val instanceof Scriptable) {
                    return (Scriptable) val;
                }

                return result;
            }
        }

        Scriptable newScriptable = ctor.construct(cx, scope, callArgs);
        if (newTargetPrototype != null) {
            newScriptable.setPrototype((Scriptable) newTargetPrototype);
        }

        return newScriptable;
    }

    private static Object defineProperty(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        // ES6 26.1.3: Reflect.defineProperty(target, propertyKey, attributes)
        // 1. If Type(target) is not Object, throw a TypeError exception.
        ScriptableObject target = checkTarget(args);

        // 2. Let key be ToPropertyKey(propertyKey).
        // 3. ReturnIfAbrupt(key).
        Object rawKey = args.length > 1 ? args[1] : Undefined.instance;
        Object key;
        if (rawKey instanceof Symbol) {
            key = rawKey;
        } else {
            key =
                    ScriptRuntime.toString(
                            ScriptRuntime.toPrimitive(rawKey, ScriptRuntime.StringClass));
        }

        // 4. Let desc be ? ToPropertyDescriptor(attributes).
        if (args.length < 3) {
            throw ScriptRuntime.typeErrorById(
                    "msg.method.missing.parameter",
                    "Reflect.defineProperty",
                    "3",
                    Integer.toString(args.length));
        }
        DescriptorInfo desc = new DescriptorInfo(ScriptableObject.ensureScriptableObject(args[2]));

        try {
            if (key instanceof Symbol) {
                return target.defineOwnProperty(cx, key, desc);
            } else {
                return target.defineOwnProperty(cx, (String) key, desc);
            }
        } catch (EcmaError e) {
            return false;
        }
    }

    private static Object deleteProperty(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        // ES6 26.1.4: Reflect.deleteProperty(target, propertyKey)
        // 1. If Type(target) is not Object, throw a TypeError exception.
        ScriptableObject target = checkTarget(args);

        // 2. Let key be ? ToPropertyKey(propertyKey).
        Object key = args.length > 1 ? args[1] : Undefined.instance;
        Object propertyKey;
        if (key instanceof Symbol) {
            propertyKey = key;
        } else {
            propertyKey =
                    ScriptRuntime.toString(
                            ScriptRuntime.toPrimitive(key, ScriptRuntime.StringClass));
        }

        // 3. Return ? target.[[Delete]](key).
        // Use deleteReturningBoolean which properly returns the result of the [[Delete]]
        // operation, including for Proxy targets where the trap may return false.
        // We catch TypeError from strict mode delete and return false in that case.
        try {
            if (propertyKey instanceof Symbol) {
                SymbolScriptable so = ScriptableObject.ensureSymbolScriptable(target);
                Symbol s = (Symbol) propertyKey;
                so.delete(s);
                return !so.has(s, target);
            } else {
                String name = (String) propertyKey;
                return target.deleteReturningBoolean(name);
            }
        } catch (EcmaError e) {
            // In strict mode, deleting non-configurable properties throws TypeError.
            // Reflect.deleteProperty should return false instead.
            if ("TypeError".equals(e.getName())
                    && e.getMessage().contains("configurable is false")) {
                return false;
            }
            throw e;
        }
    }

    private static Object get(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        ScriptableObject target = checkTarget(args);

        if (args.length < 2) {
            return Undefined.SCRIPTABLE_UNDEFINED;
        }

        // ES6 26.1.6: receiver is optional, defaults to target
        Scriptable receiver =
                args.length > 2 && args[2] instanceof Scriptable ? (Scriptable) args[2] : target;

        Object key = args[1];
        return getWithReceiver(cx, scope, target, key, receiver);
    }

    /**
     * Internal [[Get]] operation that properly passes receiver to getters. Walks up the prototype
     * chain looking for the property.
     */
    private static Object getWithReceiver(
            Context cx, Scriptable scope, Scriptable obj, Object key, Scriptable receiver) {
        // Walk up the prototype chain
        while (obj != null) {
            if (obj instanceof ScriptableObject) {
                ScriptableObject so = (ScriptableObject) obj;
                DescriptorInfo desc = so.getOwnPropertyDescriptor(cx, key);
                if (desc != null) {
                    // Found the property
                    if (desc.isAccessorDescriptor()) {
                        // Call getter with receiver as this
                        Object getter = desc.getter;
                        if (getter == null
                                || getter == NOT_FOUND
                                || Undefined.isUndefined(getter)) {
                            return Undefined.SCRIPTABLE_UNDEFINED;
                        }
                        return ((Function) getter)
                                .call(cx, scope, receiver, ScriptRuntime.emptyArgs);
                    } else {
                        // Data property - return the value
                        return desc.value;
                    }
                }
            } else {
                // Non-ScriptableObject - try direct access (no receiver support)
                Object result;
                if (ScriptRuntime.isSymbol(key)) {
                    if (obj instanceof SymbolScriptable) {
                        result = ((SymbolScriptable) obj).get((Symbol) key, obj);
                    } else {
                        result = Scriptable.NOT_FOUND;
                    }
                } else if (key instanceof Number) {
                    result = obj.get(ScriptRuntime.toIndex(key), obj);
                } else {
                    result = obj.get(ScriptRuntime.toString(key), obj);
                }
                if (result != Scriptable.NOT_FOUND) {
                    return result;
                }
            }
            obj = obj.getPrototype();
        }
        return Undefined.SCRIPTABLE_UNDEFINED;
    }

    private static Scriptable getOwnPropertyDescriptor(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        ScriptableObject target = checkTarget(args);

        if (args.length > 1) {
            if (ScriptRuntime.isSymbol(args[1])) {
                var desc = target.getOwnPropertyDescriptor(cx, args[1]);
                return desc == null ? Undefined.SCRIPTABLE_UNDEFINED : desc.toObject(scope);
            }

            var desc = target.getOwnPropertyDescriptor(cx, ScriptRuntime.toString(args[1]));
            return desc == null ? Undefined.SCRIPTABLE_UNDEFINED : desc.toObject(scope);
        }
        return Undefined.SCRIPTABLE_UNDEFINED;
    }

    private static Scriptable getPrototypeOf(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        ScriptableObject target = checkTarget(args);

        return target.getPrototype();
    }

    private static Object has(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        ScriptableObject target = checkTarget(args);

        if (args.length > 1) {
            if (ScriptRuntime.isSymbol(args[1])) {
                return ScriptableObject.hasProperty(target, (Symbol) args[1]);
            }

            return ScriptableObject.hasProperty(target, ScriptRuntime.toString(args[1]));
        }
        return false;
    }

    private static Object isExtensible(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        ScriptableObject target = checkTarget(args);
        return target.isExtensible();
    }

    private static Scriptable ownKeys(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        ScriptableObject target = checkTarget(args);

        Object[] ids;
        try (var map = target.startCompoundOp(false)) {
            ids = target.getIds(map, true, true);
        }

        // For Proxy objects, preserve the exact order returned by [[OwnPropertyKeys]].
        // For ordinary objects, reorder to: string keys first, then symbol keys.
        // Per ECMA-262, ordinary objects order keys as: integer indices (ascending),
        // then string keys (creation order), then symbol keys (creation order).
        // Proxy objects return keys in whatever order the trap specifies.
        if (target instanceof NativeProxy) {
            // Convert any non-string/symbol keys to strings
            Object[] keys = new Object[ids.length];
            for (int i = 0; i < ids.length; i++) {
                Object o = ids[i];
                if (o instanceof Symbol) {
                    keys[i] = o;
                } else {
                    keys[i] = ScriptRuntime.toString(o);
                }
            }
            return cx.newArray(scope, keys);
        }

        // For ordinary objects, separate strings and symbols
        final List<Object> strings = new ArrayList<>();
        final List<Object> symbols = new ArrayList<>();

        for (Object o : ids) {
            if (o instanceof Symbol) {
                symbols.add(o);
            } else {
                strings.add(ScriptRuntime.toString(o));
            }
        }

        Object[] keys = new Object[strings.size() + symbols.size()];
        System.arraycopy(strings.toArray(), 0, keys, 0, strings.size());
        System.arraycopy(symbols.toArray(), 0, keys, strings.size(), symbols.size());

        return cx.newArray(scope, keys);
    }

    private static Object preventExtensions(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        ScriptableObject target = checkTarget(args);

        return target.preventExtensions();
    }

    private static Object set(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        ScriptableObject target = checkTarget(args);
        if (args.length < 2) {
            return true;
        }

        Object key = args[1];
        Object value = args.length > 2 ? args[2] : Undefined.instance;

        // ES6 26.1.13: receiver is optional, defaults to target
        Object receiverArg = args.length > 3 ? args[3] : target;

        // ES6 9.1.9 [[Set]] step 5.b: If Type(Receiver) is not Object, return false.
        if (!(receiverArg instanceof Scriptable)) {
            return false;
        }
        Scriptable receiverObj = (Scriptable) receiverArg;
        ScriptableObject receiver =
                receiverArg instanceof ScriptableObject ? (ScriptableObject) receiverArg : null;

        // For Proxy targets, use putReturningBoolean which properly handles the trap result
        if (target instanceof NativeProxy && key instanceof String) {
            return target.putReturningBoolean((String) key, receiverObj, value);
        }

        // Module namespace objects always return false from [[Set]]
        if (target instanceof org.mozilla.javascript.es6module.NativeModuleNamespace) {
            return false;
        }

        // For ordinary objects, implement [[Set]] logic
        // Get the property descriptor from target
        DescriptorInfo ownDesc = target.getOwnPropertyDescriptor(cx, key);

        // If ownDesc is undefined, walk up the prototype chain
        if (ownDesc == null) {
            Scriptable parent = target.getPrototype();
            if (parent instanceof ScriptableObject) {
                ownDesc = ((ScriptableObject) parent).getOwnPropertyDescriptor(cx, key);
            }
        }

        // Check if it's a data descriptor with writable=false
        if (ownDesc != null && !ownDesc.isAccessorDescriptor()) {
            if (!ownDesc.isWritable()) {
                return false;
            }
        }

        // Check if it's an accessor descriptor
        if (ownDesc != null && ownDesc.isAccessorDescriptor()) {
            Object setter = ownDesc.setter;
            if (setter == null || setter == NOT_FOUND) {
                return false;
            }
            ((Function) setter).call(cx, scope, receiverObj, new Object[] {value});
            return true;
        }

        // Perform the actual set on receiver
        if (receiver == null) {
            // Receiver is a non-ScriptableObject Scriptable - just return true
            // as we can't properly set on it
            return true;
        }

        // For Proxy receivers, use [[DefineOwnProperty]] to avoid infinite recursion
        // (Proxy set trap calling Reflect.set which calls put which triggers set trap again)
        if (receiver instanceof NativeProxy) {
            // ES6 9.1.9.2 step 5.c: Get receiver's existing property
            DescriptorInfo existingDesc = receiver.getOwnPropertyDescriptor(cx, key);
            if (existingDesc != null) {
                // ES6 9.1.9.2 step 5.d.i: If accessor, return false
                if (existingDesc.isAccessorDescriptor()) {
                    return false;
                }
                // ES6 9.1.9.2 step 5.d.ii: If not writable, return false
                if (!existingDesc.isWritable()) {
                    return false;
                }
                // ES6 9.1.9.2 step 5.d.iv: Call [[DefineOwnProperty]] with just {value: V}
                DescriptorInfo valueDesc =
                        new DescriptorInfo(
                                NOT_FOUND, NOT_FOUND, NOT_FOUND, NOT_FOUND, NOT_FOUND, value);
                return receiver.defineOwnProperty(cx, key, valueDesc, false);
            } else {
                // ES6 9.1.9.2 step 5.e: CreateDataProperty(Receiver, P, V)
                DescriptorInfo newDesc = new DescriptorInfo(true, true, true, value);
                return receiver.defineOwnProperty(cx, key, newDesc, false);
            }
        }

        // For non-Proxy receivers (including TypedArrays), check existing property then use put
        DescriptorInfo existingDesc = receiver.getOwnPropertyDescriptor(cx, key);
        if (existingDesc != null) {
            // If it's an accessor on receiver, return false
            if (existingDesc.isAccessorDescriptor()) {
                return false;
            }
            // If receiver's property is not writable, return false
            if (!existingDesc.isWritable()) {
                return false;
            }
        }

        // Use put which will invoke the receiver's [[Set]] internal method
        // This is needed for TypedArrays and other exotic objects with custom [[Set]] behavior
        if (ScriptRuntime.isSymbol(key)) {
            receiver.put((Symbol) key, receiver, value);
        } else {
            StringIdOrIndex s = ScriptRuntime.toStringIdOrIndex(key);
            if (s.stringId == null) {
                receiver.put(s.index, receiver, value);
            } else {
                receiver.put(s.stringId, receiver, value);
            }
        }

        return true;
    }

    private static Object setPrototypeOf(
            Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        if (args.length < 2) {
            throw ScriptRuntime.typeErrorById(
                    "msg.method.missing.parameter",
                    "Reflect.js_setPrototypeOf",
                    "2",
                    Integer.toString(args.length));
        }

        ScriptableObject target = checkTarget(args);

        if (target.getPrototype() == args[1]) {
            return true;
        }

        if (!target.isExtensible()) {
            return false;
        }

        if (args[1] == null) {
            target.setPrototype(null);
            return true;
        }

        if (ScriptRuntime.isSymbol(args[1])) {
            throw ScriptRuntime.typeErrorById("msg.arg.not.object", ScriptRuntime.typeof(args[0]));
        }

        ScriptableObject proto = ScriptableObject.ensureScriptableObject(args[1]);
        if (target.getPrototype() == proto) {
            return true;
        }

        // avoid cycles
        Scriptable p = proto;
        while (p != null) {
            if (target == p) {
                return false;
            }
            p = p.getPrototype();
        }

        target.setPrototype(proto);
        return true;
    }

    private static ScriptableObject checkTarget(Object[] args) {
        if (args.length == 0 || args[0] == null || args[0] == Undefined.instance) {
            Object argument = args.length == 0 ? Undefined.instance : args[0];
            throw ScriptRuntime.typeErrorById(
                    "msg.no.properties", ScriptRuntime.toString(argument));
        }

        if (ScriptRuntime.isSymbol(args[0])) {
            throw ScriptRuntime.typeErrorById("msg.arg.not.object", ScriptRuntime.typeof(args[0]));
        }
        return ScriptableObject.ensureScriptableObject(args[0]);
    }
}
