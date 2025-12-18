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

public class GetElem implements Instruction {
    private final Operand lhs;
    private final Operand property;

    public GetElem(Operand lhs, Operand property) {
        this.lhs = lhs;
        this.property = property;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;

        Object value;
        if (!this.property.isDouble(frame)) {
            var id = property.retrieve(cx, frame);
            var lhs = this.lhs.retrieveAndWrap(cx, frame);
            value = ScriptRuntime.getObjectElem(lhs, id, cx, frame.scope);
        } else {
            double d = property.retrieveDouble(frame);
            var lhs = this.lhs.retrieveAndWrap(cx, frame);
            value = ScriptRuntime.getObjectIndex(lhs, d, cx, frame.scope);
        }

        frame.push(value);
    }

    @Override
    public int stackChange() {
        return 1 + lhs.stackChange() + property.stackChange();
    }

    @Override
    public String toDebugString() {
        return "GetElem(lhs=" + lhs + ", property=" + property + ")";
    }
}
