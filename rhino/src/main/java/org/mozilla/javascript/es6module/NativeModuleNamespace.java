/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.es6module;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import org.mozilla.javascript.CompoundOperationMap;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Symbol;
import org.mozilla.javascript.SymbolKey;
import org.mozilla.javascript.SymbolScriptable;

/**
 * Implementation of the Module Namespace Exotic Object per the ECMAScript specification.
 *
 * <p>Module namespace objects have special [[Get]], [[Set]], [[Delete]], and [[OwnPropertyKeys]]
 * internal methods that restrict access to the module's exports.
 *
 * @see <a href="https://tc39.es/ecma262/#sec-module-namespace-exotic-objects">Module Namespace
 *     Exotic Objects</a>
 */
public class NativeModuleNamespace extends ScriptableObject implements SymbolScriptable {
    private static final long serialVersionUID = 1L;

    public static final String CLASS_NAME = "Module";

    private final ModuleRecord module;
    private final Set<String> exports;

    /**
     * Creates a module namespace object for the given module.
     *
     * @param module the module record
     * @param exports the sorted set of export names
     */
    public NativeModuleNamespace(ModuleRecord module, Set<String> exports) {
        this.module = module;
        // Keep exports sorted per spec
        this.exports = Collections.unmodifiableSortedSet(new TreeSet<>(exports));
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    /** Returns the module record associated with this namespace. */
    public ModuleRecord getModule() {
        return module;
    }

    /** Returns the set of export names. */
    public Set<String> getExports() {
        return exports;
    }

    // Module namespace objects have their own [[Get]] that only allows accessing exports
    @Override
    public Object get(String name, org.mozilla.javascript.Scriptable start) {
        if (!exports.contains(name)) {
            return NOT_FOUND;
        }
        return module.getExportBinding(name);
    }

    @Override
    public Object get(Symbol key, org.mozilla.javascript.Scriptable start) {
        if (SymbolKey.TO_STRING_TAG.equals(key)) {
            return CLASS_NAME;
        }
        return NOT_FOUND;
    }

    private static boolean isStrictMode() {
        Context cx = Context.getCurrentContext();
        return (cx != null) && cx.isStrictMode();
    }

    // Module namespace objects are not extensible - cannot add properties
    @Override
    public void put(String name, org.mozilla.javascript.Scriptable start, Object value) {
        if (isStrictMode()) {
            throw ScriptRuntime.typeErrorById("msg.modify.readonly", name);
        }
        // Silently ignore in non-strict mode
    }

    @Override
    public void put(Symbol key, org.mozilla.javascript.Scriptable start, Object value) {
        if (isStrictMode()) {
            throw ScriptRuntime.typeErrorById("msg.modify.readonly", key.toString());
        }
        // Silently ignore in non-strict mode
    }

    /**
     * Module namespace [[Set]] always returns false per ES6 spec 10.4.6.9.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-module-namespace-exotic-objects-set-p-v-receiver">
     *     [[Set]]</a>
     */
    @Override
    public boolean putReturningBoolean(
            String name, org.mozilla.javascript.Scriptable start, Object value) {
        if (isStrictMode()) {
            throw ScriptRuntime.typeErrorById("msg.modify.readonly", name);
        }
        return false;
    }

    // Cannot delete properties from module namespace objects
    @Override
    public void delete(String name) {
        if (exports.contains(name)) {
            throw ScriptRuntime.typeErrorById("msg.delete.property.with.configurable.false", name);
        }
        // Non-existent properties can be "deleted" (no-op)
    }

    @Override
    public void delete(Symbol key) {
        // Cannot delete Symbol.toStringTag
        if (SymbolKey.TO_STRING_TAG.equals(key)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.delete.property.with.configurable.false", key.toString());
        }
    }

    // Has checks if the name is in exports
    @Override
    public boolean has(String name, org.mozilla.javascript.Scriptable start) {
        return exports.contains(name);
    }

    @Override
    public boolean has(Symbol key, org.mozilla.javascript.Scriptable start) {
        return SymbolKey.TO_STRING_TAG.equals(key);
    }

    // OwnPropertyKeys returns the sorted export names
    // Per ES6 spec, [[OwnPropertyKeys]] should NOT throw for TDZ - it just returns the keys.
    // The TDZ check happens in [[GetOwnProperty]] which is called during Object.keys() and
    // for-in enumeration via EnumerableOwnProperties.
    @Override
    public Object[] getIds() {
        return exports.toArray(new Object[0]);
    }

    /**
     * Checks if an export binding is in the Temporal Dead Zone and throws if so.
     *
     * <p>This is called during property enumeration and access to ensure that uninitialized
     * bindings throw ReferenceError per the ES6 module namespace specification.
     *
     * @param name the export name to check
     * @throws ReferenceError if the binding is in TDZ
     */
    public void checkBindingTDZ(String name) {
        if (!exports.contains(name)) {
            return;
        }
        // getExportBinding will throw ReferenceError if the binding is in TDZ
        module.getExportBinding(name);
    }

    @Override
    public Object[] getAllIds() {
        return exports.toArray(new Object[0]);
    }

    /**
     * Module namespace [[OwnPropertyKeys]] returns sorted export names followed by symbol keys.
     *
     * <p>Per ES6 spec, [[OwnPropertyKeys]] should NOT throw for TDZ - it just returns the keys.
     *
     * @see <a
     *     href="https://tc39.es/ecma262/#sec-module-namespace-exotic-objects-ownpropertykeys">[[OwnPropertyKeys]]</a>
     */
    @Override
    protected Object[] getIds(
            CompoundOperationMap map, boolean getNonEnumerable, boolean getSymbols) {
        if (getSymbols) {
            // Return sorted exports plus Symbol.toStringTag
            Object[] result = new Object[exports.size() + 1];
            int i = 0;
            for (String name : exports) {
                result[i++] = name;
            }
            result[i] = SymbolKey.TO_STRING_TAG;
            return result;
        } else {
            return exports.toArray(new Object[0]);
        }
    }

    /**
     * Returns the attributes for a property. For module namespace objects, this must check TDZ
     * because Object.prototype.propertyIsEnumerable calls this via isEnumerable(), and per ES6 spec
     * it should call [[GetOwnProperty]] which triggers [[Get]] and throws for TDZ.
     *
     * @param name the property name
     * @return the property attributes (READONLY for exports, NOT_FOUND attributes if not an export)
     */
    @Override
    public int getAttributes(String name) {
        if (exports.contains(name)) {
            // Check TDZ - getExportBinding will throw ReferenceError if binding is uninitialized
            checkBindingTDZ(name);
            // Module namespace properties are writable, enumerable, non-configurable
            return PERMANENT;
        }
        // Property doesn't exist
        return super.getAttributes(name);
    }

    /**
     * Module namespace [[GetOwnProperty]] returns property descriptors for exports.
     *
     * @see <a
     *     href="https://tc39.es/ecma262/#sec-module-namespace-exotic-objects-getownproperty-p">[[GetOwnProperty]]</a>
     */
    @Override
    protected DescriptorInfo getOwnPropertyDescriptor(Context cx, Object id) {
        // 1. If Type(P) is Symbol, return OrdinaryGetOwnProperty(O, P)
        if (id instanceof Symbol) {
            Symbol sym = (Symbol) id;
            if (SymbolKey.TO_STRING_TAG.equals(sym)) {
                // Symbol.toStringTag: { [[Value]]: "Module", [[Writable]]: false,
                // [[Enumerable]]: false, [[Configurable]]: false }
                return new DescriptorInfo(false, false, false, CLASS_NAME);
            }
            return null;
        }

        String name = ScriptRuntime.toString(id);

        // 2-3. If P is not an element of exports, return undefined
        if (!exports.contains(name)) {
            return null;
        }

        // 4-5. Return PropertyDescriptor { [[Value]]: value, [[Writable]]: true,
        //      [[Enumerable]]: true, [[Configurable]]: false }
        Object value = module.getExportBinding(name);
        return new DescriptorInfo(true, true, false, value);
    }

    /**
     * Module namespace [[DefineOwnProperty]] - returns true only if no change is requested.
     *
     * @see <a
     *     href="https://tc39.es/ecma262/#sec-module-namespace-exotic-objects-defineownproperty-p-desc">[[DefineOwnProperty]]</a>
     */
    @Override
    protected boolean defineOwnProperty(
            Context cx, Object id, DescriptorInfo desc, boolean checkValid) {
        // 1. If Type(P) is Symbol, return OrdinaryDefineOwnProperty(O, P, Desc)
        if (id instanceof Symbol) {
            Symbol sym = (Symbol) id;
            if (SymbolKey.TO_STRING_TAG.equals(sym)) {
                // Symbol.toStringTag exists but is non-configurable
                // Check if desc would make any change
                return isNoOpDescriptor(desc, CLASS_NAME, false, false, false);
            }
            // Other symbols don't exist
            return false;
        }

        String name = ScriptRuntime.toString(id);

        // 2. Let current be O.[[GetOwnProperty]](P)
        // 3. If current is undefined, return false
        if (!exports.contains(name)) {
            return false;
        }

        // 4. If Desc has [[Configurable]] field and Desc.[[Configurable]] is true, return false
        if (desc.hasConfigurable() && desc.isConfigurable()) {
            return false;
        }

        // 5. If Desc has [[Enumerable]] field and Desc.[[Enumerable]] is false, return false
        if (desc.hasEnumerable() && !desc.isEnumerable()) {
            return false;
        }

        // 6. If IsAccessorDescriptor(Desc) is true, return false
        if (desc.isAccessorDescriptor()) {
            return false;
        }

        // 7. If Desc has [[Writable]] field and Desc.[[Writable]] is false, return false
        if (desc.hasWritable() && !desc.isWritable()) {
            return false;
        }

        // 8. If Desc has [[Value]] field, return SameValue(Desc.[[Value]], current.[[Value]])
        if (desc.hasValue()) {
            Object currentValue = module.getExportBinding(name);
            return ScriptRuntime.shallowEq(desc.value, currentValue);
        }

        // 9. Return true
        return true;
    }

    /** Helper to check if a descriptor makes no changes to an existing property. */
    private boolean isNoOpDescriptor(
            DescriptorInfo desc,
            Object currentValue,
            boolean currentWritable,
            boolean currentEnumerable,
            boolean currentConfigurable) {
        if (desc.hasConfigurable() && desc.isConfigurable() != currentConfigurable) {
            return false;
        }
        if (desc.hasEnumerable() && desc.isEnumerable() != currentEnumerable) {
            return false;
        }
        if (desc.isAccessorDescriptor()) {
            return false;
        }
        if (desc.hasWritable() && desc.isWritable() != currentWritable) {
            return false;
        }
        if (desc.hasValue() && !ScriptRuntime.shallowEq(desc.value, currentValue)) {
            return false;
        }
        return true;
    }

    // Module namespace objects are not extensible
    @Override
    public boolean isExtensible() {
        return false;
    }

    // Prevent making it extensible
    @Override
    public boolean preventExtensions() {
        // Already not extensible, return true
        return true;
    }

    // Get prototype returns null for module namespace objects
    @Override
    public org.mozilla.javascript.Scriptable getPrototype() {
        return null;
    }

    // Cannot set prototype on module namespace objects
    @Override
    public void setPrototype(org.mozilla.javascript.Scriptable prototype) {
        // Silently ignore per spec
    }
}
