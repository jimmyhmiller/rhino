/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.interpreterv2.operand.Operand;

public class SetElem implements Instruction {
    private final Operand lhs;
    private final Operand elem;
    private final Operand rhs;

    public SetElem(Operand lhs, Operand elem, Operand rhs) {
        this.lhs = lhs;
        this.elem = elem;
        this.rhs = rhs;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;

        var rhs = this.rhs.retrieveAndWrap(cx, frame);
        Object value;
        if (!this.elem.isDouble(frame)) {
            var id = this.elem.retrieve(cx, frame);
            var lhs = this.lhs.retrieveAndWrap(cx, frame);
            value = ScriptRuntime.setObjectElem(lhs, id, rhs, cx, frame.scope);
        } else {
            double d = this.elem.retrieveDouble(frame);
            var lhs = this.lhs.retrieveAndWrap(cx, frame);
            value = ScriptRuntime.setObjectIndex(lhs, d, rhs, cx, frame.scope);
        }

        frame.push(value);
    }

    @Override
    public int stackChange() {
        return 1 + lhs.stackChange() + elem.stackChange() + rhs.stackChange();
    }

    @Override
    public String toDebugString() {
        return "SetElem(lhs=" + lhs + ", elem=" + elem + ", rhs=" + rhs + ")";
    }
}
