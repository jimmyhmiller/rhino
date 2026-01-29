/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.mozilla.javascript.Token;

/**
 * AST node for an ES6 class declaration or expression.
 *
 * <p>Node type is {@link Token#CLASS}.
 *
 * <pre><i>ClassDeclaration</i> :
 *       <b>class</b> BindingIdentifier ClassTail
 * <i>ClassExpression</i> :
 *       <b>class</b> BindingIdentifieropt ClassTail
 * <i>ClassTail</i> :
 *       ClassHeritageopt <b>{</b> ClassBodyopt <b>}</b>
 * <i>ClassHeritage</i> :
 *       <b>extends</b> LeftHandSideExpression
 * <i>ClassBody</i> :
 *       ClassElementList
 * <i>ClassElementList</i> :
 *       ClassElement
 *       ClassElementList ClassElement
 * <i>ClassElement</i> :
 *       MethodDefinition
 *       <b>static</b> MethodDefinition
 *       <b>;</b></pre>
 */
public class ClassNode extends AstNode {

    /** Class is a statement (class declaration) */
    public static final int CLASS_STATEMENT = 1;

    /** Class is an expression */
    public static final int CLASS_EXPRESSION = 2;

    private static final List<ClassElement> NO_ELEMENTS =
            Collections.unmodifiableList(new ArrayList<>());

    private Name className;
    private AstNode superClass;
    private List<ClassElement> elements;
    private int classType;
    private int lc = -1; // position of '{'
    private int rc = -1; // position of '}'

    {
        type = Token.CLASS;
    }

    public ClassNode() {}

    public ClassNode(int pos) {
        super(pos);
    }

    public ClassNode(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns the class name, or {@code null} for anonymous class expressions.
     *
     * @return the class name node
     */
    public Name getClassName() {
        return className;
    }

    /**
     * Sets the class name.
     *
     * @param className the class name node, or {@code null} for anonymous class expressions
     */
    public void setClassName(Name className) {
        this.className = className;
        if (className != null) {
            className.setParent(this);
        }
    }

    /**
     * Returns the identifier (as a string) for the class name, or {@code null} if anonymous.
     *
     * @return the class name as a string
     */
    public String getClassNameString() {
        return className != null ? className.getIdentifier() : null;
    }

    /**
     * Returns the superclass expression (the part after 'extends'), or {@code null} if there is no
     * extends clause.
     *
     * @return the superclass expression
     */
    public AstNode getSuperClass() {
        return superClass;
    }

    /**
     * Sets the superclass expression.
     *
     * @param superClass the superclass expression, or {@code null}
     */
    public void setSuperClass(AstNode superClass) {
        this.superClass = superClass;
        if (superClass != null) {
            superClass.setParent(this);
        }
    }

    /**
     * Returns true if this class has an extends clause.
     *
     * @return true if there is a superclass
     */
    public boolean hasExtends() {
        return superClass != null;
    }

    /**
     * Returns the list of class elements (methods, static methods). Returns an immutable empty list
     * if there are no elements.
     *
     * @return the class elements
     */
    public List<ClassElement> getElements() {
        return elements != null ? elements : NO_ELEMENTS;
    }

    /**
     * Sets the list of class elements.
     *
     * @param elements the class elements
     */
    public void setElements(List<ClassElement> elements) {
        if (elements == null) {
            this.elements = null;
        } else {
            if (this.elements != null) this.elements.clear();
            for (ClassElement e : elements) addElement(e);
        }
    }

    /**
     * Adds a class element (method definition).
     *
     * @param element the class element to add
     */
    public void addElement(ClassElement element) {
        assertNotNull(element);
        if (elements == null) {
            elements = new ArrayList<>();
        }
        elements.add(element);
        element.setParent(this);
    }

    /**
     * Returns the constructor method, or {@code null} if none defined. The constructor is the
     * method named "constructor".
     *
     * @return the constructor FunctionNode
     */
    public FunctionNode getConstructor() {
        for (ClassElement element : getElements()) {
            if (!element.isStatic() && element.isConstructor()) {
                return element.getMethod();
            }
        }
        return null;
    }

    /**
     * Returns the class type (CLASS_STATEMENT or CLASS_EXPRESSION).
     *
     * @return the class type
     */
    public int getClassType() {
        return classType;
    }

    /**
     * Sets the class type.
     *
     * @param classType CLASS_STATEMENT or CLASS_EXPRESSION
     */
    public void setClassType(int classType) {
        this.classType = classType;
    }

    /**
     * Returns true if this is a class statement (declaration).
     *
     * @return true for class declarations
     */
    public boolean isStatement() {
        return classType == CLASS_STATEMENT;
    }

    /**
     * Returns true if this is a class expression.
     *
     * @return true for class expressions
     */
    public boolean isExpression() {
        return classType == CLASS_EXPRESSION;
    }

    /**
     * Returns the position of the left brace.
     *
     * @return position of '{'
     */
    public int getLc() {
        return lc;
    }

    /**
     * Sets the position of the left brace.
     *
     * @param lc position of '{'
     */
    public void setLc(int lc) {
        this.lc = lc;
    }

    /**
     * Returns the position of the right brace.
     *
     * @return position of '}'
     */
    public int getRc() {
        return rc;
    }

    /**
     * Sets the position of the right brace.
     *
     * @param rc position of '}'
     */
    public void setRc(int rc) {
        this.rc = rc;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append("class");
        if (className != null) {
            sb.append(" ");
            sb.append(className.toSource(0));
        }
        if (superClass != null) {
            sb.append(" extends ");
            sb.append(superClass.toSource(0));
        }
        sb.append(" {\n");
        for (ClassElement element : getElements()) {
            sb.append(element.toSource(depth + 1));
            sb.append("\n");
        }
        sb.append(makeIndent(depth));
        sb.append("}");
        return sb.toString();
    }

    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            if (className != null) {
                className.visit(v);
            }
            if (superClass != null) {
                superClass.visit(v);
            }
            for (ClassElement element : getElements()) {
                element.visit(v);
            }
        }
    }
}
