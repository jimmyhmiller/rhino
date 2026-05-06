package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.JSFunction;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.interpreterv2.InstructionFormatter;
import org.mozilla.javascript.interpreterv2.operand.Operand;

public class FunctionStoreHomeObject implements Instruction {

    private final Operand closureOperand;
    private final Operand homeObjectOperand;

    public FunctionStoreHomeObject(Operand closureOperand, Operand homeObjectOperand) {
        this.closureOperand = closureOperand;
        this.homeObjectOperand = homeObjectOperand;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;

        // Stack contains: [object, function]
        JSFunction fun = (JSFunction) closureOperand.retrieve(cx, frame);
        Scriptable homeObject = (Scriptable) homeObjectOperand.retrieve(cx, frame);
        fun.setHomeObject(homeObject);
    }

    @Override
    public int stackChange() {
        return 0;
    }

    @Override
    public String toDebugString() {
        return InstructionFormatter.formatInstruction(
                this, "closure", closureOperand, "homeObject", homeObjectOperand);
    }
}
