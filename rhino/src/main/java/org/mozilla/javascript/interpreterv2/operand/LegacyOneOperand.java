package org.mozilla.javascript.interpreterv2.operand;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;

public class LegacyOneOperand implements Operand {
    public static final LegacyOneOperand instance = new LegacyOneOperand();

    private LegacyOneOperand() {}

    @Override
    public Integer retrieve(Context cx, CallFrameV2 frame) {
        return 1;
    }

    @Override
    public double retrieveDouble(CallFrameV2 frame) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDouble(CallFrameV2 frame) {
        return false;
    }

    @Override
    public void appendDebugString(StringBuilder sb) {
        sb.append("1 legacy");
    }

    @Override
    public boolean isValidJumpTableKey() {
        return true;
    }
}
