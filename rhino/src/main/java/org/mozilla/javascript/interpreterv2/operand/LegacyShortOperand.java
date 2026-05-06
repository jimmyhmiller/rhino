package org.mozilla.javascript.interpreterv2.operand;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;

public class LegacyShortOperand implements Operand {

    // It is implicitly cast to an int in old interpreter
    private final int value;

    public LegacyShortOperand(short value) {
        this.value = value;
    }

    @Override
    public Integer retrieve(Context cx, CallFrameV2 frame) {
        return value;
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
        sb.append(value).append(" legacy");
    }

    @Override
    public boolean isValidJumpTableKey() {
        return true;
    }
}
