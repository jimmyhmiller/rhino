package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;

public class ReturnSubroutine implements Instruction {
    private final int returnPcOffset;

    public ReturnSubroutine(int returnPcOffset) {
        this.returnPcOffset = returnPcOffset;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;

        // Normal return from GOSUB
        if (frame.hasSubRoutineReturnPC(returnPcOffset)) {
            var returnPc = frame.getSubRoutineReturnPC(returnPcOffset);
            frame.pc = (int) returnPc;
            frame.pcPrevBranch = frame.pc;
            return;
        }

        // Invocation from exception handler, restore object to rethrow
        frame.throwable = frame.getLocal(returnPcOffset);
    }

    @Override
    public int stackChange() {
        return 0;
    }
}
