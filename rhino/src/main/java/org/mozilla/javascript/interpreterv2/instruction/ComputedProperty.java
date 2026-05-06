package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Symbol;
import org.mozilla.javascript.interpreterv2.InstructionFormatter;
import org.mozilla.javascript.interpreterv2.operand.Operand;

public class ComputedProperty implements Instruction {
    private final Operand key;
    private final Object[] ids;
    private final int index;

    public ComputedProperty(Operand key, Object[] ids, int index) {
        this.key = key;
        this.ids = ids;
        this.index = index;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;

        var key = this.key.retrieveAndWrap(cx, frame);
        // ToPropertyKey: must be performed before evaluating the value expression
        // (ECMA 13.2.5.5 PropertyDefinitionEvaluation step 1).
        ids[index] = key instanceof Symbol ? key : ScriptRuntime.toString(key);
    }

    @Override
    public int stackChange() {
        return key.stackChange();
    }

    @Override
    public String toDebugString() {
        return InstructionFormatter.formatInstruction(this, "key", key, "index", index);
    }
}
