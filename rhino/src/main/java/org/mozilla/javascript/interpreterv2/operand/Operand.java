package org.mozilla.javascript.interpreterv2.operand;

import java.math.BigInteger;
import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.interpreterv2.InstructionSimplification;
import org.mozilla.javascript.interpreterv2.KnownType;

public interface Operand {
    Operand[] EMPTY_ARRAY = new Operand[0];

    Object retrieve(Context cx, CallFrameV2 frame);

    double retrieveDouble(CallFrameV2 frame);

    boolean isDouble(CallFrameV2 frame);

    default int stackChange() {
        return 0;
    }

    default Operand convertToConsume() {
        return this;
    }

    default void cleanup(CallFrameV2 frame) {}

    default Object viewValue(Context cx, CallFrameV2 frame, int offset) {
        return retrieve(cx, frame);
    }

    default boolean isValidJumpTableKey() {
        return false;
    }

    default Object retrieveAndWrap(Context cx, CallFrameV2 frame) {
        if (isDouble(frame)) {
            return ScriptRuntime.wrapNumber(retrieveDouble(frame));
        }
        return retrieve(cx, frame);
    }

    default Number retrieveNumber(Context cx, CallFrameV2 frame) {
        if (isDouble(frame)) {
            return retrieveDouble(frame);
        } else {
            var obj = retrieve(cx, frame);
            return ScriptRuntime.toNumeric(obj);
        }
    }

    default boolean coerceToBoolean(Context cx, CallFrameV2 frame) {
        if (this.isDouble(frame)) {
            double d = this.retrieveDouble(frame);
            return !Double.isNaN(d) && d != 0.0;
        }

        Object x = this.retrieve(cx, frame);
        if (Boolean.TRUE.equals(x)) {
            return true;
        } else if (Boolean.FALSE.equals(x)) {
            return false;
        } else if (x == null || Undefined.isUndefined(x)) {
            return false;
        } else if (x instanceof BigInteger) {
            return !x.equals(BigInteger.ZERO);
        } else if (x instanceof Number) {
            double d = ((Number) x).doubleValue();
            return (!Double.isNaN(d) && d != 0.0);
        } else {
            return ScriptRuntime.toBoolean(x);
        }
    }

    default double coerceToDouble(Context cx, CallFrameV2 frame) {
        if (this.isDouble(frame)) {
            return retrieveDouble(frame);
        }
        return ScriptRuntime.toNumber(retrieve(cx, frame));
    }

    default void setResult(Context cx, CallFrameV2 frame) {
        if (isDouble(frame)) {
            frame.setResult(retrieveDouble(frame));
        } else {
            frame.setResult(retrieve(cx, frame));
        }
    }

    default KnownType getKnownType(InstructionSimplification simplifier) {
        return KnownType.UNKNOWN;
    }

    void appendDebugString(StringBuilder sb);
}
