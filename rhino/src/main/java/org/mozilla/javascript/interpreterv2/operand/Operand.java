/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.interpreterv2.operand;

import java.math.BigInteger;
import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.interpreterv2.InstructionSimplification;
import org.mozilla.javascript.interpreterv2.KnownType;

/** Base interface for operands that retrieve values for instructions. */
public interface Operand {
    Operand[] EMPTY_ARRAY = new Operand[0];

    /**
     * Retrieve the value of this operand.
     *
     * @param cx The context
     * @param frame The current call frame
     * @return The operand value
     */
    Object retrieve(Context cx, CallFrameV2 frame);

    /**
     * Retrieve the value as a double.
     *
     * @param frame The current call frame
     * @return The double value
     */
    double retrieveDouble(CallFrameV2 frame);

    /**
     * Check if this operand represents a double value.
     *
     * @param frame The current call frame
     * @return true if the value is a double
     */
    boolean isDouble(CallFrameV2 frame);

    /**
     * Get the stack change caused by retrieving this operand.
     *
     * @return The stack change (typically 0 or -1)
     */
    default int stackChange() {
        return 0;
    }

    /**
     * Convert this operand to a consuming version (e.g., peek to pop).
     *
     * @return A consuming operand
     */
    default Operand convertToConsume() {
        return this;
    }

    /**
     * Clean up after this operand has been used.
     *
     * @param frame The current call frame
     */
    default void cleanup(CallFrameV2 frame) {}

    /**
     * View the value without consuming it, at a given offset.
     *
     * @param cx The context
     * @param frame The current call frame
     * @param offset Stack offset for viewing
     * @return The value
     */
    default Object viewValue(Context cx, CallFrameV2 frame, int offset) {
        return retrieve(cx, frame);
    }

    /**
     * Check if this operand can be used as a jump table key.
     *
     * @return true if valid as a jump table key
     */
    default boolean isValidJumpTableKey() {
        return false;
    }

    /**
     * Retrieve the value and wrap numbers if needed.
     *
     * @param cx The context
     * @param frame The current call frame
     * @return The wrapped value
     */
    default Object retrieveAndWrap(Context cx, CallFrameV2 frame) {
        if (isDouble(frame)) {
            return ScriptRuntime.wrapNumber(retrieveDouble(frame));
        }
        return retrieve(cx, frame);
    }

    /**
     * Retrieve the value as a Number.
     *
     * @param cx The context
     * @param frame The current call frame
     * @return The numeric value
     */
    default Number retrieveNumber(Context cx, CallFrameV2 frame) {
        if (isDouble(frame)) {
            return retrieveDouble(frame);
        } else {
            var obj = retrieve(cx, frame);
            return ScriptRuntime.toNumeric(obj);
        }
    }

    /**
     * Coerce the value to a boolean.
     *
     * @param cx The context
     * @param frame The current call frame
     * @return The boolean value
     */
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

    /**
     * Coerce the value to a double.
     *
     * @param cx The context
     * @param frame The current call frame
     * @return The double value
     */
    default double coerceToDouble(Context cx, CallFrameV2 frame) {
        if (this.isDouble(frame)) {
            return retrieveDouble(frame);
        }
        return ScriptRuntime.toNumber(retrieve(cx, frame));
    }

    /**
     * Set this operand's value as the frame result.
     *
     * @param cx The context
     * @param frame The current call frame
     */
    default void setResult(Context cx, CallFrameV2 frame) {
        if (isDouble(frame)) {
            frame.setResult(retrieveDouble(frame));
        } else {
            frame.setResult(retrieve(cx, frame));
        }
    }

    /**
     * Get the known type of this operand for optimization.
     *
     * @param simplifier The simplification context
     * @return The known type
     */
    default KnownType getKnownType(InstructionSimplification simplifier) {
        return KnownType.UNKNOWN;
    }

    /**
     * Append a debug string representation.
     *
     * @param sb The string builder
     */
    void appendDebugString(StringBuilder sb);
}
