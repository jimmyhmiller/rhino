package org.mozilla.javascript.interpreterv2.instruction;

import java.util.Arrays;
import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NewLiteralStorage;
import org.mozilla.javascript.Node;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.interpreterv2.InstructionFormatter;
import org.mozilla.javascript.interpreterv2.operand.Operand;

/**
 * Builds an object literal, routing construction through {@link NewLiteralStorage} so that both
 * plain and spread entries ({@code {...expr}}) are handled uniformly.
 */
public class ObjectLit implements Instruction {
    private final Operand objectOperand;
    private final Object[] keys;
    private final Operand[] values;
    private final int[] getterSetters;

    /** Null when no spread is present. Otherwise parallel to {@code values}. */
    private final boolean[] isSpread;

    /**
     * Number of non-spread entries; equals {@code values.length} when {@link #isSpread} is null.
     */
    private final int nonSpreadCount;

    private final boolean copyKeys;

    public ObjectLit(
            Operand objectOperand,
            Object[] keys,
            Operand[] values,
            int[] getterSetters,
            boolean[] isSpread,
            int nonSpreadCount,
            boolean copyKeys) {
        this.objectOperand = objectOperand;
        this.keys = keys;
        this.values = values;
        this.getterSetters = getterSetters;
        this.isSpread = isSpread;
        this.nonSpreadCount = nonSpreadCount;
        this.copyKeys = copyKeys;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;

        Object[] vals;
        if (values.length == 0) {
            vals = ScriptRuntime.emptyArgs;
        } else {
            vals = new Object[values.length];
            int totalPops = 0;
            for (var v : values) {
                totalPops += v.stackChange();
            }
            int pops = totalPops;
            for (int i = 0; i < values.length; i++) {
                vals[i] = values[i].viewValue(cx, frame, pops);
                pops -= values[i].stackChange();
            }
            frame.popN(-totalPops);
        }

        Object[] keys = copyKeys ? Arrays.copyOf(this.keys, this.keys.length) : this.keys;

        var storage = NewLiteralStorage.create(cx, nonSpreadCount, true);
        for (int i = 0; i < values.length; i++) {
            if (isSpread != null && isSpread[i]) {
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
        Object[] cleanedProperties =
                Arrays.stream(keys)
                        .map(
                                p ->
                                        p instanceof Node
                                                        && ((Node) p).getType()
                                                                == Token.COMPUTED_PROPERTY
                                                ? "#"
                                                : p)
                        .toArray();

        if (isSpread != null) {
            return InstructionFormatter.formatInstruction(
                    this,
                    "object",
                    objectOperand,
                    "spread",
                    isSpread,
                    "keys",
                    cleanedProperties,
                    "nonSpreadCount",
                    nonSpreadCount);
        }
        return InstructionFormatter.formatInstruction(
                this,
                "object",
                objectOperand,
                "properties",
                cleanedProperties,
                "copyKeys",
                copyKeys);
    }
}
