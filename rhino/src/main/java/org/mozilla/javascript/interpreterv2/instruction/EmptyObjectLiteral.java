package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.interpreterv2.InstructionFormatter;

/**
 * Puts an empty object on the stack. This is necessary for methods to reference back to set their
 * home objects
 */
public class EmptyObjectLiteral implements Instruction {
    public static final EmptyObjectLiteral instance = new EmptyObjectLiteral();

    private EmptyObjectLiteral() {}

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;

        frame.push(cx.newObject(frame.scope));
    }

    @Override
    public int stackChange() {
        return 1;
    }

    @Override
    public String toDebugString() {
        return InstructionFormatter.formatInstruction(this);
    }
}
