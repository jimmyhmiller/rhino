/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.interpreterv2;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.mozilla.javascript.interpreterv2.instruction.Instruction;

/** Performs optimization passes on instruction sequences. */
public class InstructionSimplification {
    private final List<Instruction> instructions;
    private final Set<Integer> jumpTargets;
    private int currentStackOffset = 0;
    private int currentPc = 0;

    public InstructionSimplification(List<Instruction> instructions, Set<Integer> jumpTargets) {
        this.instructions = instructions;
        this.jumpTargets = jumpTargets;
    }

    /** Run simplification passes over all instructions. */
    public void simplify() {
        for (int pc = 0; pc < instructions.size(); pc++) {
            this.currentPc = pc;
            resetForInstruction();

            Instruction instruction = instructions.get(pc);
            Instruction simplified = instruction.simplify(this);

            if (instruction != simplified) {
                instructions.set(pc, simplified);
            }
        }
    }

    /**
     * Get the known type of a value at a given stack offset.
     *
     * @param stackOffset Offset from current stack top (0 = top)
     * @return The known type, or UNKNOWN if not determinable
     */
    public KnownType getStackValueType(int stackOffset) {
        var producerPc = findStackProducer(stackOffset);
        if (producerPc.isEmpty()) {
            return KnownType.UNKNOWN;
        }

        int pc = producerPc.get();
        int savedPc = this.currentPc;
        this.currentPc = pc;
        KnownType type = instructions.get(pc).getKnownType(this);
        this.currentPc = savedPc;
        return type;
    }

    /**
     * Check if a given PC is a jump target.
     *
     * @param pc The program counter
     * @return true if this PC is a jump target
     */
    public boolean isJumpTarget(int pc) {
        return jumpTargets.contains(pc);
    }

    /**
     * Record that stack values have been consumed.
     *
     * @param count Number of values consumed
     */
    public void consumeStack(int count) {
        currentStackOffset += count;
    }

    private Optional<Integer> findStackProducer(int stackOffset) {
        int totalOffset = currentStackOffset + stackOffset;

        if (isJumpTarget(currentPc)) {
            return Optional.empty();
        }

        int remainingDepth = totalOffset;

        for (int i = currentPc - 1; i >= 0; i--) {

            Instruction inst = instructions.get(i);
            int change = inst.stackChange();

            remainingDepth -= change;

            if (change > 0 && remainingDepth < 0) {
                return Optional.of(i);
            }

            if (isJumpTarget(i)) {
                return Optional.empty();
            }
        }

        throw new IllegalStateException("We didn't find the producer of this stack value");
    }

    private void resetForInstruction() {
        currentStackOffset = 0;
    }
}
