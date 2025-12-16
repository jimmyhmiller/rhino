/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.interpreterv2.operand;

import static org.mozilla.javascript.UniqueTag.DOUBLE_MARK;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.interpreterv2.InstructionSimplification;
import org.mozilla.javascript.interpreterv2.KnownType;

/** Operand that peeks a value from the stack without consuming it. */
public class PeekOperand implements Operand {
    public static final PeekOperand instance = new PeekOperand();

    private final int offset;

    public PeekOperand() {
        offset = 0;
    }

    public PeekOperand(int offset) {
        this.offset = offset;
    }

    @Override
    public Object retrieve(Context cx, CallFrameV2 frame) {
        return frame.peek(offset);
    }

    @Override
    public double retrieveDouble(CallFrameV2 frame) {
        return frame.peekDouble(offset);
    }

    @Override
    public boolean isDouble(CallFrameV2 frame) {
        return frame.peek(offset) == DOUBLE_MARK;
    }

    @Override
    public Operand convertToConsume() {
        return PopOperand.instance;
    }

    @Override
    public void cleanup(CallFrameV2 frame) {
        frame.pop();
    }

    @Override
    public KnownType getKnownType(InstructionSimplification simplifier) {
        return simplifier.getStackValueType(offset);
    }

    @Override
    public void appendDebugString(StringBuilder sb) {
        sb.append("peek(offset=").append(offset).append(")");
    }
}
