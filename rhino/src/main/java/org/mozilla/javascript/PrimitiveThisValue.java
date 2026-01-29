/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

/**
 * A special Scriptable wrapper for primitive values used as 'this' in strict mode functions.
 *
 * <p>In strict mode, primitive values passed as 'this' should NOT be converted to their wrapper
 * objects. For example, in strict mode: <code>
 * function fun() { return this instanceof String; }
 * fun.call(""); // should return false, 'this' is primitive string ""
 * </code> This class wraps primitive values so they can be passed through Rhino's Scriptable-based
 * 'this' mechanism while preserving the information that the original value was a primitive.
 *
 * <p>When 'this' is accessed in a strict function, the interpreter/compiler should unwrap this to
 * return the actual primitive value.
 */
public class PrimitiveThisValue implements Scriptable, SymbolScriptable {
    private final Object primitiveValue;
    private final Scriptable wrapper;

    /**
     * Create a PrimitiveThisValue wrapping the given primitive and its object wrapper.
     *
     * @param primitiveValue The actual primitive value (String, Number, Boolean)
     * @param wrapper The Scriptable wrapper (NativeString, NativeNumber, NativeBoolean) for
     *     property access
     */
    public PrimitiveThisValue(Object primitiveValue, Scriptable wrapper) {
        this.primitiveValue = primitiveValue;
        this.wrapper = wrapper;
    }

    /**
     * @return The original primitive value
     */
    public Object getPrimitiveValue() {
        return primitiveValue;
    }

    /**
     * @return The Scriptable wrapper for property access
     */
    public Scriptable getWrapper() {
        return wrapper;
    }

    // Delegate all Scriptable methods to the wrapper for property access
    // These are needed when accessing properties like this.length on a string

    @Override
    public String getClassName() {
        return wrapper.getClassName();
    }

    @Override
    public Object get(String name, Scriptable start) {
        return wrapper.get(name, start);
    }

    @Override
    public Object get(int index, Scriptable start) {
        return wrapper.get(index, start);
    }

    @Override
    public boolean has(String name, Scriptable start) {
        return wrapper.has(name, start);
    }

    @Override
    public boolean has(int index, Scriptable start) {
        return wrapper.has(index, start);
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        wrapper.put(name, start, value);
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
        wrapper.put(index, start, value);
    }

    @Override
    public void delete(String name) {
        wrapper.delete(name);
    }

    @Override
    public void delete(int index) {
        wrapper.delete(index);
    }

    @Override
    public Scriptable getPrototype() {
        return wrapper.getPrototype();
    }

    @Override
    public void setPrototype(Scriptable prototype) {
        wrapper.setPrototype(prototype);
    }

    @Override
    public Scriptable getParentScope() {
        return wrapper.getParentScope();
    }

    @Override
    public void setParentScope(Scriptable parent) {
        wrapper.setParentScope(parent);
    }

    @Override
    public Object[] getIds() {
        return wrapper.getIds();
    }

    @Override
    public Object getDefaultValue(Class<?> hint) {
        return wrapper.getDefaultValue(hint);
    }

    @Override
    public boolean hasInstance(Scriptable instance) {
        return wrapper.hasInstance(instance);
    }

    // SymbolScriptable methods - delegate to wrapper if it supports symbols

    @Override
    public Object get(Symbol key, Scriptable start) {
        if (wrapper instanceof SymbolScriptable) {
            return ((SymbolScriptable) wrapper).get(key, start);
        }
        return Scriptable.NOT_FOUND;
    }

    @Override
    public boolean has(Symbol key, Scriptable start) {
        if (wrapper instanceof SymbolScriptable) {
            return ((SymbolScriptable) wrapper).has(key, start);
        }
        return false;
    }

    @Override
    public void put(Symbol key, Scriptable start, Object value) {
        if (wrapper instanceof SymbolScriptable) {
            ((SymbolScriptable) wrapper).put(key, start, value);
        }
    }

    @Override
    public void delete(Symbol key) {
        if (wrapper instanceof SymbolScriptable) {
            ((SymbolScriptable) wrapper).delete(key);
        }
    }
}
