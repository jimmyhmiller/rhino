package org.mozilla.javascript;

import java.util.EnumSet;
import org.mozilla.javascript.debug.DebuggableScript;

/**
 * Represents a JavaScript function built upon a a {@link JSDescriptor}. All the immutable metadata
 * associated with the function is stored on the descriptor, with only the lexically bound this, the
 * home object required for super calls, and mutable properties held on the function object itself.
 */
public class JSFunction extends BaseFunction implements ScriptOrFn<JSFunction> {
    private final JSDescriptor<JSFunction> descriptor;
    private final Scriptable lexicalThis;
    // Non-final: for derived class constructors, homeObject is set after construction
    // by ScriptRuntime.createClass() once the class prototype is created.
    private Scriptable homeObject;

    public JSFunction(
            Context cx,
            Scriptable scope,
            JSDescriptor<JSFunction> descriptor,
            Scriptable lexicalThis,
            Scriptable homeObject) {
        this.descriptor = descriptor;
        this.lexicalThis = lexicalThis;
        this.homeObject = homeObject;
        ScriptRuntime.setFunctionProtoAndParent(this, cx, scope, descriptor.isES6Generator());
        if (!descriptor.isShorthand()) {
            setupDefaultPrototype(scope);
        }
        // ES6 generator functions, strict functions, arrow functions, and method definitions
        // should not have own "arguments" or "caller" properties - they inherit throwing
        // accessors from Function.prototype. The properties were added by
        // BaseFunction.createProperties() before descriptor was set, so we remove them here.
        if (shouldRemoveRestrictedProperties()) {
            removeRestrictedPropertiesForGenerator();
        }
    }

    JSFunction(
            Scriptable scope,
            JSDescriptor<JSFunction> descriptor,
            Scriptable lexicalThis,
            Scriptable homeObject) {
        this.descriptor = descriptor;
        this.lexicalThis = lexicalThis;
        this.homeObject = homeObject;
        setParentScope(scope);
        setPrototype(ScriptableObject.getFunctionPrototype(scope));
        // ES6 generator functions, strict functions, arrow functions, and method definitions
        // should not have own "arguments" or "caller" properties
        if (shouldRemoveRestrictedProperties()) {
            removeRestrictedPropertiesForGenerator();
        }
    }

    private boolean shouldRemoveRestrictedProperties() {
        // These function types should NOT have own "caller" and "arguments" properties.
        // They inherit throwing accessors from Function.prototype.
        return descriptor.isES6Generator()
                || descriptor.isStrict()
                || descriptor.hasLexicalThis() // arrow functions
                || descriptor.isShorthand(); // method definitions
    }

    @Override
    protected void setPrototypeProperty(Object prototype) {
        super.setPrototypeProperty(prototype);
    }

    @Override
    public Scriptable getDeclarationScope() {
        return this.getParentScope();
    }

    @Override
    public JSDescriptor<JSFunction> getDescriptor() {
        return descriptor;
    }

    @Override
    final String decompile(int indent, EnumSet<DecompilerFlag> flags) {
        return descriptor.getRawSource();
    }

    public boolean isShorthand() {
        return descriptor.isShorthand();
    }

    public boolean isStrict() {
        return descriptor.isStrict();
    }

    @Override
    protected boolean includeNonStandardProps() {
        // Strict functions should NOT have own "caller" and "arguments" properties.
        // They should inherit the throwing accessors from Function.prototype.
        if (descriptor != null && isStrict()) {
            return false;
        }
        return super.includeNonStandardProps();
    }

    @Override
    public int getArity() {
        return descriptor.getArity();
    }

    public DebuggableScript getDebuggableView() {
        return null;
    }

    protected int getLanguageVersion() {
        return descriptor.getLanguageVersion();
    }

    @Override
    protected boolean hasPrototypeProperty() {
        return true;
    }

    @Override
    protected boolean isGeneratorFunction() {
        return descriptor != null && descriptor.isES6Generator();
    }

    @Override
    public int getLength() {
        int arity = descriptor.getArity();
        if (getLanguageVersion() != Context.VERSION_1_2) {
            return arity;
        }
        Context cx = Context.getContext();
        NativeCall activation = ScriptRuntime.findFunctionActivation(cx, this);
        if (activation == null) {
            return arity;
        }
        return activation.originalArgs.length;
    }

    protected int getParamAndVarCount() {
        return descriptor.getParamAndVarCount();
    }

    protected int getParamCount() {
        int count = descriptor.getParamCount();
        if (descriptor.hasRestArg()) {
            return count - 1;
        }
        return count;
    }

    protected boolean getParamOrVarConst(int index) {
        return descriptor.getParamOrVarConst(index);
    }

    protected boolean getParamOrVarLetOrConst(int index) {
        return descriptor.getParamOrVarLetOrConst(index);
    }

    protected String getParamOrVarName(int index) {
        return descriptor.getParamOrVarName(index);
    }

    public String getRawSource() {
        return descriptor.getRawSource();
    }

    @Override
    protected void createPrototypeProperty() {
        if (descriptor.hasPrototype()) {
            super.createPrototypeProperty();
        }
    }

    JSCode<JSFunction> getCode() {
        return descriptor.getCode();
    }

    JSCode<JSFunction> getConstructor() {
        return descriptor.getConstructor();
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        if (!ScriptRuntime.hasTopCall(cx)) {
            return ScriptRuntime.doTopCall(this, cx, scope, thisObj, args, isStrict());
        }
        var realThis = descriptor.hasLexicalThis() ? lexicalThis : thisObj;
        return descriptor.getCode().execute(cx, this, Undefined.instance, scope, realThis, args);
    }

    @Override
    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
        if (descriptor.getConstructor() == null) {
            throw ScriptRuntime.typeErrorById("msg.not.ctor", getFunctionName());
        }
        var thisObj = homeObject == null ? createObject(cx, scope) : null;
        // Pass `this` in as new.target for now. This can change when
        // the public `construct` signature changes.
        var res = descriptor.getConstructor().execute(cx, this, this, scope, thisObj, args);
        if (res instanceof Scriptable) {
            thisObj = (Scriptable) res;
        }
        return thisObj;
    }

    public boolean isScript() {
        return descriptor.isScript();
    }

    @Override
    protected boolean hasDefaultParameters() {
        return descriptor.hasDefaultParameters();
    }

    public boolean hasFunctionNamed(String name) {
        return descriptor.hasFunctionNamed(name);
    }

    @Override
    public String getFunctionName() {
        return descriptor.getName();
    }

    public Object resumeGenerator(
            Context cx, Scriptable scope, int operation, Object state, Object value) {
        return descriptor.getCode().resume(cx, this, state, scope, operation, value);
    }

    @Override
    public Scriptable getHomeObject() {
        return homeObject;
    }

    @Override
    public void setHomeObject(Scriptable homeObject) {
        this.homeObject = homeObject;
    }

    public Scriptable getFunctionThis(Scriptable functionThis) {
        if (descriptor.hasLexicalThis()) {
            return this.lexicalThis;
        } else {
            return functionThis;
        }
    }

    /** Create script from compiled bytecode. */
    public static JSScript createScript(
            JSDescriptor<JSScript> desc, Scriptable homeObject, Object staticSecurityDomain) {
        assert (desc.getSecurityDomain() == staticSecurityDomain);
        assert desc.isScript();
        return new JSScript(desc, homeObject);
    }

    /** Create function compiled from Function(...) constructor. */
    public static JSFunction createFunction(
            Context cx,
            Scriptable scope,
            JSDescriptor<JSFunction> desc,
            Scriptable homeObject,
            Object staticSecurityDomain) {
        assert (desc.getSecurityDomain() == staticSecurityDomain);
        JSFunction f = new JSFunction(cx, scope, desc, null, homeObject);
        return f;
    }

    /** Create function embedded in script or another function. */
    static JSFunction createFunction(
            Context cx,
            Scriptable scope,
            JSDescriptor<?> parent,
            int index,
            Scriptable homeObject) {
        JSDescriptor<JSFunction> desc = parent.getFunction(index);
        JSFunction f = new JSFunction(cx, scope, desc, null, homeObject);
        return f;
    }

    @Override
    public boolean isConstructor() {
        return descriptor.getConstructor() != null;
    }
}
