/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;

public class Pop implements Instruction {
    public static final Pop instance = new Pop();

    private Pop() {}

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pop();
        frame.pc += 1;
    }

    @Override
    public int stackChange() {
        return -1;
    }
}
