package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.interpreterv2.operand.Operand;

public class SetName implements Instruction {
    private final Operand lhs;
    private final String name;
    private final Operand rhs;

    public SetName(Operand lhs, String name, Operand rhs) {
        this.lhs = lhs;
        this.name = name;
        this.rhs = rhs;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;

        var rhs = this.rhs.retrieveAndWrap(cx, frame);
        var lhs = (Scriptable) this.lhs.retrieve(cx, frame);

        frame.push(ScriptRuntime.setName(lhs, rhs, cx, frame.scope, name));
    }

    @Override
    public int stackChange() {
        return 1 + lhs.stackChange() + rhs.stackChange();
    }

    @Override
    public String toDebugString() {
        return "SetName{lhs=" + lhs + ", name=" + name + ", rhs=" + rhs + "}";
    }
}
