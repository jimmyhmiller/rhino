package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;

public class EnumId implements Instruction {
    private final int localBlockRef;

    public EnumId(int localBlockRef) {
        this.localBlockRef = localBlockRef;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        Object val = frame.getLocal(localBlockRef);
        frame.push(ScriptRuntime.enumId(val, cx));

        frame.pc += 1;
    }

    @Override
    public int stackChange() {
        return 1;
    }
}
