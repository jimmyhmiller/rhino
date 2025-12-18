/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;

public class Dup implements Instruction {
    public static final Dup instance = new Dup();

    private Dup() {}

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;
        frame.push(frame.peek(), frame.peekDouble());
    }

    @Override
    public int stackChange() {
        return 1;
    }
}
