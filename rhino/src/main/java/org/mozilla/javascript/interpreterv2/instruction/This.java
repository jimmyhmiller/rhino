package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;

public class This implements Instruction {
    public static final This instance = new This();

    private This() {}

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.push(frame.thisObj);
        frame.pc += 1;
    }

    @Override
    public int stackChange() {
        return 1;
    }

    @Override
    public String toDebugString() {
        return "This{}";
    }
}
