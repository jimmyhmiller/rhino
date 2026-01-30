/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

/**
 * AST node representing a class element (method or field definition) in an ES6 class body.
 *
 * <p>Node type is {@link Token#METHOD} for methods or {@link Token#FIELD} for fields.
 *
 * <pre><i>ClassElement</i> :
 *       MethodDefinition
 *       <b>static</b> MethodDefinition
 *       FieldDefinition <b>;</b>
 *       <b>static</b> FieldDefinition <b>;</b>
 *       <b>;</b>
 * <i>MethodDefinition</i> :
 *       PropertyName <b>(</b> UniqueFormalParameters <b>)</b> <b>{</b> FunctionBody <b>}</b>
 *       GeneratorMethod
 *       AsyncMethod
 *       AsyncGeneratorMethod
 *       <b>get</b> PropertyName <b>(</b> <b>)</b> <b>{</b> FunctionBody <b>}</b>
 *       <b>set</b> PropertyName <b>(</b> PropertySetParameterList <b>)</b> <b>{</b> FunctionBody <b>}</b>
 * <i>FieldDefinition</i> :
 *       ClassElementName Initializer<sub>opt</sub>
 * </pre>
 */
public class ClassElement extends AstNode {

    private AstNode propertyName; // Name, StringLiteral, NumberLiteral, or ComputedPropertyKey
    private FunctionNode method;
    private AstNode initializer; // For field definitions, the initializer expression (optional)
    private boolean isStatic;
    private boolean isComputed; // true if property name is computed [expr]
    private boolean isField; // true for field definitions, false for methods
    private boolean isPrivate; // true for private members (#name)

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
     * Returns true if this is a field definition (not a method).
     *
     * @return true for field definitions
     */
    public boolean isField() {
        return isField;
    }

    /**
     * Sets whether this is a field definition.
     *
     * @param isField true for field definitions
     */
    public void setIsField(boolean isField) {
        this.isField = isField;
        if (isField) {
            this.type = Token.FIELD;
        }
    }

    /**
     * Returns true if this is a private member (#name).
     *
     * @return true for private members
     */
    public boolean isPrivate() {
        return isPrivate;
    }

    /**
     * Sets whether this is a private member.
     *
     * @param isPrivate true for private members
     */
    public void setIsPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    /**
     * Returns the initializer expression for field definitions.
     *
     * @return the initializer expression, or null if none
     */
    public AstNode getInitializer() {
        return initializer;
    }

    /**
     * Sets the initializer expression for field definitions.
     *
     * @param initializer the initializer expression
     */
    public void setInitializer(AstNode initializer) {
        this.initializer = initializer;
        if (initializer != null) {
            initializer.setParent(this);
        }
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
            if (isPrivate) {
                sb.append("#");
            }
            sb.append(propertyName.toSource(0));
        }
        if (isField) {
            // Field definition
            if (initializer != null) {
                sb.append(" = ");
                sb.append(initializer.toSource(0));
            }
            sb.append(";");
        } else if (method != null) {
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
            if (initializer != null) {
                initializer.visit(v);
            }
        }
    }
}
