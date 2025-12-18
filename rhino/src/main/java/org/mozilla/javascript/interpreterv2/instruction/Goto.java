package org.mozilla.javascript.interpreterv2.instruction;

import java.util.Collections;
import java.util.Set;
import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;

public class Goto extends JumpInstruction {

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += offset;
        frame.pcPrevBranch = frame.pc;
    }

    @Override
    public int stackChange() {
        return 0;
    }

    @Override
    public Set<Integer> getTargets(int fromPC) {
        return Collections.singleton(fromPC + offset);
    }
}
