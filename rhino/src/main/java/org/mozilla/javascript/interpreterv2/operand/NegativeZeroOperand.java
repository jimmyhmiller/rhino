/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.interpreterv2.operand;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;

/** Operand representing negative zero. */
public class NegativeZeroOperand implements Operand {
    public static final NegativeZeroOperand instance = new NegativeZeroOperand();

    private NegativeZeroOperand() {}

    @Override
    public Double retrieve(Context cx, CallFrameV2 frame) {
        return ScriptRuntime.negativeZeroObj;
    }

    @Override
    public double retrieveDouble(CallFrameV2 frame) {
        return ScriptRuntime.negativeZeroObj;
    }

    @Override
    public boolean isDouble(CallFrameV2 frame) {
        return true;
    }

    @Override
    public void appendDebugString(StringBuilder sb) {
        sb.append("-0");
    }

    @Override
    public boolean isValidJumpTableKey() {
        return true;
    }
}
