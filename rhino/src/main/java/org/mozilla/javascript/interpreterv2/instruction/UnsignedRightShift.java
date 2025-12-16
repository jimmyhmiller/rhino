/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.interpreterv2.InstructionFormatter;
import org.mozilla.javascript.interpreterv2.operand.Operand;

/** Unsigned right shift instruction. */
public class UnsignedRightShift implements Instruction {
    private final Operand lhs;
    private final Operand rhs;

    public UnsignedRightShift(Operand lhs, Operand rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;

        double rValue = 0;
        Object rObj = null;
        boolean shouldCoerceRight = false;
        if (rhs.isDouble(frame)) {
            rValue = rhs.retrieveDouble(frame);
        } else {
            rObj = rhs.retrieve(cx, frame);
            shouldCoerceRight = true;
        }
        double lValue = 0;
        Object lObj = null;
        boolean shouldCoerceLeft = false;
        if (lhs.isDouble(frame)) {
            lValue = lhs.retrieveDouble(frame);
        } else {
            lObj = lhs.retrieve(cx, frame);
            shouldCoerceLeft = true;
        }

        if (shouldCoerceLeft) {
            lValue = ScriptRuntime.toNumber(lObj);
        }
        if (shouldCoerceRight) {
            rValue = ScriptRuntime.toInt32(rObj);
        }

        long value = ScriptRuntime.toUint32(lValue) >>> ((int) rValue & 0x1F);
        frame.push((double) value);
    }

    @Override
    public int stackChange() {
        return 1 + lhs.stackChange() + rhs.stackChange();
    }

    @Override
    public String toDebugString() {
        return InstructionFormatter.formatInstruction(this, "lhs", lhs, "rhs", rhs);
    }
}
