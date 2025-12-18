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

public class ArrayLit implements Instruction {
    private final Operand[] elements;
    private final int[] skipIndices;

    public ArrayLit(Operand[] elements, int[] skipIndices) {
        this.elements = elements;
        this.skipIndices = skipIndices;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;

        Object[] data = frame.getArguments(cx, elements);
        Object val = ScriptRuntime.newArrayLiteral(data, skipIndices, cx, frame.scope);
        frame.push(val);
    }

    @Override
    public int stackChange() {
        int count = 0;
        for (var element : elements) {
            count += element.stackChange();
        }
        return 1 + count;
    }

    @Override
    public String toDebugString() {
        var length = skipIndices == null ? 0 : skipIndices.length;
        StringBuilder sb = new StringBuilder();
        sb.append("ArrayLit{elements=[");
        for (int i = 0; i < elements.length; i++) {
            if (i > 0) sb.append(", ");
            elements[i].appendDebugString(sb);
        }
        sb.append("], skipIndices=");
        sb.append(length);
        sb.append("}");
        return sb.toString();
    }
}
