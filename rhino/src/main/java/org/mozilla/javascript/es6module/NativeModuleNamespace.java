/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.es6module;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
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
    @Override
    public Object[] getIds() {
        return exports.toArray(new Object[0]);
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
