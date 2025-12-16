/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.interpreterv2.instruction;

import static org.mozilla.javascript.UniqueTag.DOUBLE_MARK;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.ConsString;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.interpreterv2.InstructionSimplification;
import org.mozilla.javascript.interpreterv2.KnownType;
import org.mozilla.javascript.interpreterv2.operand.Operand;

/** Optimized string concatenation when left operand is a known string. */
public class StringAnyAdd implements Instruction {
    private final Operand lhs;
    private final Operand rhs;

    public StringAnyAdd(Operand lhs, Operand rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;

        double doubleValue = 0.0;
        Object value;
        if (rhs.isDouble(frame)) {
            doubleValue = rhs.retrieveDouble(frame);
            value = DOUBLE_MARK;
        } else {
            value = rhs.retrieve(cx, frame);
        }

        CharSequence rhsValue;
        if (value == DOUBLE_MARK) {
            rhsValue = ScriptRuntime.numberToString(doubleValue, 10);
        } else if (value instanceof Scriptable) {
            Object primitive = ScriptRuntime.toPrimitive(value);
            rhsValue =
                    (primitive instanceof CharSequence)
                            ? (CharSequence) primitive
                            : ScriptRuntime.toString(primitive);
        } else if (value instanceof CharSequence) {
            rhsValue = (CharSequence) value;
        } else {
            rhsValue = ScriptRuntime.toCharSequence(value);
        }

        CharSequence lhsValue = (CharSequence) lhs.retrieve(cx, frame);

        frame.push(new ConsString(lhsValue, rhsValue));
    }

    @Override
    public int stackChange() {
        return 1 + lhs.stackChange() + rhs.stackChange();
    }

    @Override
    public KnownType getKnownType(InstructionSimplification simplifier) {
        return KnownType.STRING;
    }
}
