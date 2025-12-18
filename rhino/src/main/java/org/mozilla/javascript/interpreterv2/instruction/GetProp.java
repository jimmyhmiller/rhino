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

public class GetProp implements Instruction {
    private final Operand lhs;
    private final String property;
    private final boolean noWarn;

    public GetProp(Operand lhs, String property, boolean noWarn) {
        this.lhs = lhs;
        this.property = property;
        this.noWarn = noWarn;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;

        var lhs = this.lhs.retrieveAndWrap(cx, frame);
        frame.push(
                !noWarn
                        ? ScriptRuntime.getObjectProp(lhs, property, cx, frame.scope)
                        : ScriptRuntime.getObjectPropNoWarn(lhs, property, cx, frame.scope));
    }

    @Override
    public int stackChange() {
        return 1 + lhs.stackChange();
    }

    @Override
    public String toDebugString() {
        return "GetProp(lhs=" + lhs + ", property=" + property + ", noWarn=" + noWarn + ")";
    }
}
