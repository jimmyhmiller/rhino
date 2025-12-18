package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;

public class EnumNext implements Instruction {
    private final int localBlockRef;

    public EnumNext(int localBlockRef) {
        this.localBlockRef = localBlockRef;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        Object val = frame.getLocal(localBlockRef);
        frame.push(ScriptRuntime.enumNext(val, cx));

        frame.pc += 1;
    }

    @Override
    public int stackChange() {
        return 1;
    }
}
