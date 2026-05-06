package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.interpreterv2.InstructionSimplification;
import org.mozilla.javascript.interpreterv2.KnownType;

public interface Instruction {
    void interpret(Context cx, CallFrameV2 frame);

    int stackChange();

    default Instruction simplify(InstructionSimplification simplifier) {
        return this;
    }

    default KnownType getKnownType(InstructionSimplification simplifier) {
        return KnownType.UNKNOWN;
    }

    default String toDebugString() {
        return getClass().getSimpleName();
    }
}
