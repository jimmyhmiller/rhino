package org.mozilla.javascript.interpreterv2.instruction;

import java.util.Collections;
import java.util.Set;
import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.interpreterv2.operand.Operand;

public class IfEqPop extends JumpInstruction {
    private final Operand value;
    private final Operand test;

    public IfEqPop(Operand value, Operand test) {
        this.value = value;
        this.test = test;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        Object val = value.retrieve(cx, frame);
        Object tst = test.retrieve(cx, frame);
        boolean condition = ScriptRuntime.shallowEq(val, tst);

        if (!condition) {
            frame.pc += 1;
        } else {
            frame.pc += offset;
            frame.pcPrevBranch = frame.pc;
            value.cleanup(frame);
        }
    }

    @Override
    public int stackChange() {
        return value.stackChange() + test.stackChange();
    }

    @Override
    public Set<Integer> getTargets(int fromPC) {
        return Collections.singleton(fromPC + offset);
    }
}
