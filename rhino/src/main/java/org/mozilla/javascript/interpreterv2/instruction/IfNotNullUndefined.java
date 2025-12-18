package org.mozilla.javascript.interpreterv2.instruction;

import java.util.Collections;
import java.util.Set;
import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.interpreterv2.operand.Operand;

public class IfNotNullUndefined extends JumpInstruction {
    private final Operand lhs;

    public IfNotNullUndefined(Operand lhs) {
        this.lhs = lhs;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        Object value = lhs.retrieve(cx, frame);
        if (value != null && value != Undefined.instance) {
            frame.pc += offset;
            frame.pcPrevBranch = frame.pc;
        } else {
            frame.pc += 1;
        }
    }

    @Override
    public int stackChange() {
        return lhs.stackChange();
    }

    @Override
    public Set<Integer> getTargets(int fromPC) {
        return Collections.singleton(fromPC + offset);
    }
}
