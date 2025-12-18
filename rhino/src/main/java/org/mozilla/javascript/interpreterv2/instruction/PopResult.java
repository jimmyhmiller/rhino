/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;

public class PopResult implements Instruction {
    public static final PopResult instance = new PopResult();

    private PopResult() {}

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.popResult();
        frame.pc += 1;
    }

    @Override
    public int stackChange() {
        return -1;
    }
}
