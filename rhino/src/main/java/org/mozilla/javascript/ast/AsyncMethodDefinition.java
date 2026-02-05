/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

/**
 * AST node for an async method definition in an object literal, i.e. `async key() {}` or `async
 * *key() {}` (async generator method) in an object literal.
 */
public class AsyncMethodDefinition extends AstNode {

    private AstNode methodName;
    private boolean isGenerator;

    public AsyncMethodDefinition(int pos, int len, AstNode methodName, boolean isGenerator) {
        super(pos, len);
        setType(Token.ASYNC);
        setMethodName(methodName);
        this.isGenerator = isGenerator;
    }

    public AstNode getMethodName() {
        return methodName;
    }

    public void setMethodName(AstNode methodName) {
        assertNotNull(methodName);
        this.methodName = methodName;
        methodName.setParent(this);
    }

    public boolean isGenerator() {
        return isGenerator;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append("async ");
        if (isGenerator) {
            sb.append("* ");
        }
        sb.append(methodName.toSource(depth));
        return sb.toString();
    }

    /** Visits this node, then the name. */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            methodName.visit(v);
        }
    }
}
