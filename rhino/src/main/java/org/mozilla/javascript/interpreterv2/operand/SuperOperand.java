/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.interpreterv2.operand;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.InterpreterV2;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

/** Operand that retrieves the 'super' base object for method calls. */
public class SuperOperand implements Operand {
    public static final SuperOperand instance = new SuperOperand();

    private SuperOperand() {}

    @Override
    public Object retrieve(Context cx, CallFrameV2 frame) {
        // See 9.1.1.3.5 GetSuperBase
        Scriptable homeObject = InterpreterV2.getCurrentFrameHomeObject(frame);
        if (homeObject == null) {
            return Undefined.instance;
        } else {
            return homeObject.getPrototype();
        }
    }

    @Override
    public double retrieveDouble(CallFrameV2 frame) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDouble(CallFrameV2 frame) {
        return false;
    }

    @Override
    public void appendDebugString(StringBuilder sb) {
        sb.append("super");
    }
}
