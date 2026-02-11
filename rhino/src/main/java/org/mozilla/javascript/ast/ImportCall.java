/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

/**
 * AST node for ES2020 dynamic {@code import()} expression. Node type is {@link Token#IMPORT_CALL}.
 *
 * <pre><i>ImportCall</i> :
 *   <b>import</b> ( AssignmentExpression )</pre>
 */
public class ImportCall extends AstNode {

    private AstNode argument;

    public ImportCall() {
        type = Token.IMPORT_CALL;
    }

    public ImportCall(int pos) {
        super(pos);
        type = Token.IMPORT_CALL;
    }

    public ImportCall(int pos, int len) {
        super(pos, len);
        type = Token.IMPORT_CALL;
    }

    public AstNode getArgument() {
        return argument;
    }

    public void setArgument(AstNode argument) {
        this.argument = argument;
        if (argument != null) argument.setParent(this);
    }

    @Override
    public String toSource(int depth) {
        return "import(" + argument.toSource(0) + ")";
    }

    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this) && argument != null) {
            argument.visit(v);
        }
    }
}
