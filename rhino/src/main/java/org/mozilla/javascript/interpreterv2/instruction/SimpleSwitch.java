package org.mozilla.javascript.interpreterv2.instruction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.ConsString;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.interpreterv2.operand.Operand;

public class SimpleSwitch extends JumpInstruction {
    private final Operand valueOperand;
    private final Map<Object, Integer> jumpTable;
    private Object[] testValues;
    private int index = 0;

    public SimpleSwitch(Operand valueOperand, List<Operand> testOperands) {
        this.valueOperand = valueOperand;

        testValues = new Object[testOperands.size()];
        jumpTable = new HashMap<>(testOperands.size());

        for (int i = 0; i < testValues.length; i++) {
            Operand operand = testOperands.get(i);
            if (operand.isDouble(null)) {
                Double d = operand.retrieveDouble(null);
                testValues[i] = normalizeZero(d);
            } else {
                Object value = operand.retrieve(null, null);
                if (value instanceof Number) {
                    Double d = ((Number) value).doubleValue();
                    testValues[i] = normalizeZero(d);
                } else {
                    assert value instanceof Boolean || value instanceof String;
                    testValues[i] = value;
                }
            }
        }
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        Object value;
        if (valueOperand.isDouble(frame)) {
            Double d = valueOperand.retrieveDouble(frame);
            value = normalizeZero(d);
        } else {
            Object rawValue = valueOperand.retrieve(cx, frame);
            if (rawValue instanceof Number) {
                Double d = ((Number) rawValue).doubleValue();
                value = normalizeZero(d);
            } else if (rawValue instanceof ConsString) {
                value = rawValue.toString();
            } else {
                value = rawValue;
            }
        }
        valueOperand.cleanup(frame);

        frame.pc += jumpTable.getOrDefault(value, offset);
    }

    @Override
    public int stackChange() {
        return valueOperand.stackChange();
    }

    @Override
    public void setOffset(int offset) {
        if (index == testValues.length) {
            testValues = null;
            this.offset = offset;
        } else {
            jumpTable.putIfAbsent(testValues[index], offset);
            index += 1;
        }
    }

    private static Double normalizeZero(Double d) {
        if (d == -0.0) {
            return 0.0;
        }
        return d;
    }

    @Override
    public Set<Integer> getTargets(int fromPC) {
        Set<Integer> targets = new HashSet<>();
        for (int caseOffset : jumpTable.values()) {
            targets.add(fromPC + caseOffset);
        }
        targets.add(fromPC + offset);
        return targets;
    }
}
