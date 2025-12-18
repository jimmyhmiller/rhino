package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;

public class Return implements Instruction {
    public static final Return instance = new Return();

    private Return() {}

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc = Integer.MAX_VALUE;
        frame.popResult();
    }

    @Override
    public int stackChange() {
        return -1;
    }
}
