/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

/**
 * AST node representing a class element (method definition) in an ES6 class body.
 *
 * <p>Node type is {@link Token#METHOD}.
 *
 * <pre><i>ClassElement</i> :
 *       MethodDefinition
 *       <b>static</b> MethodDefinition
 *       <b>;</b>
 * <i>MethodDefinition</i> :
 *       PropertyName <b>(</b> UniqueFormalParameters <b>)</b> <b>{</b> FunctionBody <b>}</b>
 *       GeneratorMethod
 *       AsyncMethod
 *       AsyncGeneratorMethod
 *       <b>get</b> PropertyName <b>(</b> <b>)</b> <b>{</b> FunctionBody <b>}</b>
 *       <b>set</b> PropertyName <b>(</b> PropertySetParameterList <b>)</b> <b>{</b> FunctionBody <b>}</b>
 * </pre>
 */
public class ClassElement extends AstNode {

    private AstNode propertyName; // Name, StringLiteral, NumberLiteral, or ComputedPropertyKey
    private FunctionNode method;
    private boolean isStatic;
    private boolean isComputed; // true if property name is computed [expr]

    {
        type = Token.METHOD;
    }

    public ClassElement() {}

    public ClassElement(int pos) {
        super(pos);
    }

    public ClassElement(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns the property name node. For regular methods, this is a {@link Name}. For computed
     * property names, this is a {@link ComputedPropertyKey}. For string/number literals, it's the
     * respective literal node.
     *
     * @return the property name node
     */
    public AstNode getPropertyName() {
        return propertyName;
    }

    /**
     * Sets the property name node.
     *
     * @param propertyName the property name
     */
    public void setPropertyName(AstNode propertyName) {
        this.propertyName = propertyName;
        if (propertyName != null) {
            propertyName.setParent(this);
        }
    }

    /**
     * Returns the property name as a string, or {@code null} for computed properties.
     *
     * @return the property name string
     */
    public String getPropertyNameString() {
        if (propertyName instanceof Name) {
            return ((Name) propertyName).getIdentifier();
        } else if (propertyName instanceof StringLiteral) {
            return ((StringLiteral) propertyName).getValue();
        }
        return null;
    }

    /**
     * Returns the method function node.
     *
     * @return the method FunctionNode
     */
    public FunctionNode getMethod() {
        return method;
    }

    /**
     * Sets the method function node.
     *
     * @param method the method FunctionNode
     */
    public void setMethod(FunctionNode method) {
        this.method = method;
        if (method != null) {
            method.setParent(this);
        }
    }

    /**
     * Returns true if this is a static method.
     *
     * @return true for static methods
     */
    public boolean isStatic() {
        return isStatic;
    }

    /**
     * Sets whether this is a static method.
     *
     * @param isStatic true for static methods
     */
    public void setIsStatic(boolean isStatic) {
        this.isStatic = isStatic;
    }

    /**
     * Returns true if the property name is computed (e.g., [expr]).
     *
     * @return true for computed property names
     */
    public boolean isComputed() {
        return isComputed;
    }

    /**
     * Sets whether the property name is computed.
     *
     * @param isComputed true for computed property names
     */
    public void setIsComputed(boolean isComputed) {
        this.isComputed = isComputed;
    }

    /**
     * Returns true if this is a constructor method.
     *
     * @return true if method name is "constructor"
     */
    public boolean isConstructor() {
        return "constructor".equals(getPropertyNameString());
    }

    /**
     * Returns true if this is a getter method.
     *
     * @return true for getters
     */
    public boolean isGetter() {
        return method != null && method.isGetterMethod();
    }

    /**
     * Returns true if this is a setter method.
     *
     * @return true for setters
     */
    public boolean isSetter() {
        return method != null && method.isSetterMethod();
    }

    /**
     * Returns true if this is a generator method.
     *
     * @return true for generator methods
     */
    public boolean isGenerator() {
        return method != null && method.isES6Generator();
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        if (isStatic) {
            sb.append("static ");
        }
        if (method != null) {
            if (method.isES6Generator()) {
                sb.append("*");
            }
            if (method.isGetterMethod()) {
                sb.append("get ");
            } else if (method.isSetterMethod()) {
                sb.append("set ");
            }
        }
        if (isComputed) {
            sb.append("[");
            sb.append(propertyName.toSource(0));
            sb.append("]");
        } else if (propertyName != null) {
            sb.append(propertyName.toSource(0));
        }
        if (method != null) {
            sb.append("(");
            // parameters
            boolean first = true;
            for (AstNode param : method.getParams()) {
                if (!first) sb.append(", ");
                sb.append(param.toSource(0));
                first = false;
            }
            sb.append(") ");
            if (method.getBody() != null) {
                sb.append(method.getBody().toSource(0));
            }
        }
        return sb.toString();
    }

    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            if (propertyName != null) {
                propertyName.visit(v);
            }
            if (method != null) {
                method.visit(v);
            }
        }
    }
}
