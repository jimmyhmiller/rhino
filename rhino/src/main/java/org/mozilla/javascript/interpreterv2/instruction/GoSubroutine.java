package org.mozilla.javascript.interpreterv2.instruction;

import java.util.Collections;
import java.util.Set;
import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;

public class GoSubroutine extends JumpInstruction {
    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.push((double) (frame.pc + 1));
        frame.pc += offset;
        frame.pcPrevBranch = frame.pc;
    }

    @Override
    public int stackChange() {
        return 1;
    }

    @Override
    public Set<Integer> getTargets(int fromPC) {
        return Collections.singleton(fromPC + offset);
    }
}
