package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Undefined;

public class ReturnUndefined implements Instruction {
    public static final ReturnUndefined instance = new ReturnUndefined();

    private ReturnUndefined() {}

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc = Integer.MAX_VALUE;
        frame.result = Undefined.instance;
    }

    @Override
    public int stackChange() {
        return 0;
    }
}
