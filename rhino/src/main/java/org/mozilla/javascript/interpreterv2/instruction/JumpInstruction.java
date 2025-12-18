package org.mozilla.javascript.interpreterv2.instruction;

import java.util.Set;

public abstract class JumpInstruction implements Instruction {
    protected int offset;

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getOffset() {
        return offset;
    }

    /**
     * Returns all possible jump targets from this instruction.
     *
     * @param fromPC the PC of this instruction
     * @return set of all possible target PCs
     */
    public abstract Set<Integer> getTargets(int fromPC);

    @Override
    public String toDebugString() {
        return this.getClass().getSimpleName() + "(offset: " + offset + ")";
    }
}
