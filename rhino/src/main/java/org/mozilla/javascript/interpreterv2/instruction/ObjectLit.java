/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.interpreterv2.instruction;

import java.util.Arrays;
import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Node;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.interpreterv2.operand.Operand;

public class ObjectLit implements Instruction {
    private final Operand objectOperand;
    private final int[] getterSetters;
    private final Operand[] values;
    private final Object[] ids;
    private final boolean copyArray;

    public ObjectLit(
            Operand objectOperand,
            int[] getterSetters,
            Operand[] values,
            Object[] ids,
            boolean copyArray) {
        this.objectOperand = objectOperand;
        this.getterSetters = getterSetters;
        this.values = values;
        this.ids = ids;
        this.copyArray = copyArray;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;

        Object[] ids = this.ids;
        if (copyArray) {
            ids = Arrays.copyOf(this.ids, ids.length);
        }

        Object[] values;

        if (this.values.length == 0) {
            values = ScriptRuntime.emptyArgs;
        } else {
            values = new Object[this.values.length];

            var totalPops = 0;
            for (var value : this.values) {
                totalPops += value.stackChange();
            }

            var pops = totalPops;
            for (int i = 0; i < values.length; i++) {
                var value = this.values[i];
                values[i] = value.viewValue(cx, frame, pops);
                pops -= this.values[i].stackChange();
            }

            frame.popN(-totalPops);
        }

        var object = (Scriptable) objectOperand.retrieve(cx, frame);
        ScriptRuntime.fillObjectLiteral(object, ids, values, getterSetters, cx, frame.scope);
    }

    @Override
    public int stackChange() {
        int count = 0;
        for (var value : values) {
            count += value.stackChange();
        }
        return objectOperand.stackChange() + count;
    }

    @Override
    public String toDebugString() {
        // Replace the computed property placeholders with a "#"
        Object[] cleanedProperties =
                Arrays.stream(ids)
                        .map(
                                p ->
                                        p instanceof Node
                                                        && ((Node) p).getType()
                                                                == Token.COMPUTED_PROPERTY
                                                ? "#"
                                                : p)
                        .toArray();

        StringBuilder sb = new StringBuilder();
        sb.append("ObjectLit{object=");
        objectOperand.appendDebugString(sb);
        sb.append(", properties=");
        sb.append(Arrays.toString(cleanedProperties));
        sb.append(", copyArray=");
        sb.append(copyArray);
        sb.append("}");
        return sb.toString();
    }
}
