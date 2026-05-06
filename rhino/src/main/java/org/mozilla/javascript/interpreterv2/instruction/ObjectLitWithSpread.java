package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NewLiteralStorage;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.interpreterv2.InstructionFormatter;
import org.mozilla.javascript.interpreterv2.operand.Operand;

/**
 * Builds an object literal that contains at least one spread ({@code {...expr}}) entry.
 *
 * <p>Uses {@link NewLiteralStorage} to accumulate properties in order, calling {@link
 * NewLiteralStorage#spread} for spread entries and {@link NewLiteralStorage#pushKey}/{@link
 * NewLiteralStorage#pushValue} for regular entries.
 */
public class ObjectLitWithSpread implements Instruction {
    private final Operand objectOperand;
    private final boolean[] isSpread;
    private final Object[] keys;
    private final Operand[] values;
    private final int[] getterSetters;
    private final int nonSpreadCount;
    private final boolean copyKeys;

    public ObjectLitWithSpread(
            Operand objectOperand,
            boolean[] isSpread,
            Object[] keys,
            Operand[] values,
            int[] getterSetters,
            int nonSpreadCount,
            boolean copyKeys) {
        this.objectOperand = objectOperand;
        this.isSpread = isSpread;
        this.keys = keys;
        this.values = values;
        this.getterSetters = getterSetters;
        this.nonSpreadCount = nonSpreadCount;
        this.copyKeys = copyKeys;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;

        int totalPops = 0;
        for (var v : values) {
            totalPops += v.stackChange();
        }

        Object[] vals = new Object[values.length];
        int pops = totalPops;
        for (int i = 0; i < values.length; i++) {
            vals[i] = values[i].viewValue(cx, frame, pops);
            pops -= values[i].stackChange();
        }
        frame.popN(-totalPops);

        Object[] keys = this.keys;
        if (copyKeys) {
            keys = keys.clone();
        }

        var storage = NewLiteralStorage.create(cx, nonSpreadCount, true);
        for (int i = 0; i < isSpread.length; i++) {
            if (isSpread[i]) {
                storage.spread(cx, frame.scope, vals[i], i);
            } else {
                storage.pushKey(keys[i]);
                switch (getterSetters[i]) {
                    case -1:
                        storage.pushGetter(vals[i]);
                        break;
                    case 1:
                        storage.pushSetter(vals[i]);
                        break;
                    default:
                        storage.pushValue(vals[i]);
                        break;
                }
            }
        }

        var object = (Scriptable) objectOperand.retrieve(cx, frame);
        ScriptRuntime.fillObjectLiteral(
                object,
                storage.getKeys(),
                storage.getValues(),
                storage.getGetterSetters(),
                cx,
                frame.scope);
    }

    @Override
    public int stackChange() {
        int count = 0;
        for (var v : values) {
            count += v.stackChange();
        }
        return objectOperand.stackChange() + count;
    }

    @Override
    public String toDebugString() {
        return InstructionFormatter.formatInstruction(
                this, "spread", isSpread, "keys", keys, "nonSpreadCount", nonSpreadCount);
    }
}
