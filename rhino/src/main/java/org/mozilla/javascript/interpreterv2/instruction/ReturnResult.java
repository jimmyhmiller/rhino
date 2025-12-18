package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;

public class ReturnResult implements Instruction {
    public static final ReturnResult instance = new ReturnResult();

    private ReturnResult() {}

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc = Integer.MAX_VALUE;
    }

    @Override
    public int stackChange() {
        return 0;
    }
}
