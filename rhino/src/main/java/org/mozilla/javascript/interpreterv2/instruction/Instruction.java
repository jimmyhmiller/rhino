/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.interpreterv2.InstructionSimplification;
import org.mozilla.javascript.interpreterv2.KnownType;

/** Base interface for all InterpreterV2 instructions. */
public interface Instruction {
    /**
     * Execute this instruction.
     *
     * @param cx The context
     * @param frame The current call frame
     */
    void interpret(Context cx, CallFrameV2 frame);

    /**
     * Return the net change to the stack caused by this instruction.
     *
     * @return The stack change (positive for push, negative for pop)
     */
    int stackChange();

    /**
     * Attempt to simplify this instruction during compilation.
     *
     * @param simplifier The simplification context
     * @return A simplified instruction, or this if no simplification is possible
     */
    default Instruction simplify(InstructionSimplification simplifier) {
        return this;
    }

    /**
     * Get the known type of the value this instruction produces.
     *
     * @param simplifier The simplification context
     * @return The known type, or UNKNOWN if not determinable
     */
    default KnownType getKnownType(InstructionSimplification simplifier) {
        return KnownType.UNKNOWN;
    }

    /**
     * Get a debug string representation of this instruction.
     *
     * @return Debug string
     */
    default String toDebugString() {
        return getClass().getSimpleName();
    }
}
