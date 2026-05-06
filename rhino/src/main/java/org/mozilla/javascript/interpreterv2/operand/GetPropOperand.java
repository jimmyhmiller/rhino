package org.mozilla.javascript.interpreterv2.operand;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;

public class GetPropOperand implements Operand {

    private final Operand left;
    private final String propertyName;
    private final boolean noWarn;

    public GetPropOperand(Operand left, String propertyName, boolean noWarn) {
        this.left = left;
        this.propertyName = propertyName;
        this.noWarn = noWarn;
    }

    @Override
    public Object retrieve(Context cx, CallFrameV2 frame) {
        Object lhs = left.retrieveAndWrap(cx, frame);
        return !noWarn
                ? ScriptRuntime.getObjectProp(lhs, propertyName, cx, frame.scope)
                : ScriptRuntime.getObjectPropNoWarn(lhs, propertyName, cx, frame.scope);
    }

    @Override
    public double retrieveDouble(CallFrameV2 frame) {
        throw new UnsupportedOperationException("GetProp operand has no double value");
    }

    @Override
    public boolean isDouble(CallFrameV2 frame) {
        return false;
    }

    @Override
    public int stackChange() {
        return left.stackChange();
    }

    @Override
    public Object viewValue(Context cx, CallFrameV2 frame, int offset) {
        Object lhs = left.viewValue(cx, frame, offset);
        return !noWarn
                ? ScriptRuntime.getObjectProp(lhs, propertyName, cx, frame.scope)
                : ScriptRuntime.getObjectPropNoWarn(lhs, propertyName, cx, frame.scope);
    }

    @Override
    public Operand convertToConsume() {
        var newLeft = left.convertToConsume();
        return new GetPropOperand(newLeft, propertyName, noWarn);
    }

    @Override
    public void cleanup(CallFrameV2 frame) {
        left.cleanup(frame);
    }

    @Override
    public void appendDebugString(StringBuilder sb) {
        sb.append('(');
        left.appendDebugString(sb);
        sb.append(").").append(propertyName);
        if (noWarn) {
            sb.append(" nowarn");
        }
    }
}
