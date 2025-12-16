/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.interpreterv2.operand;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Undefined;

/** Operand representing the undefined value. */
public class UndefinedOperand implements Operand {

    public static final UndefinedOperand instance = new UndefinedOperand();

    private UndefinedOperand() {}

    @Override
    public Object retrieve(Context cx, CallFrameV2 frame) {
        return Undefined.instance;
    }

    @Override
    public double retrieveDouble(CallFrameV2 frame) {
        throw new UnsupportedOperationException("Undefined operand has no double value");
    }

    @Override
    public boolean isDouble(CallFrameV2 frame) {
        return false;
    }

    @Override
    public void appendDebugString(StringBuilder sb) {
        sb.append("undefined");
    }

    @Override
    public boolean isValidJumpTableKey() {
        return false;
    }
}
