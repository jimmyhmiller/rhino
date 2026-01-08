/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Map;
import org.mozilla.javascript.interpreterv2.CompilerData;
import org.mozilla.javascript.interpreterv2.LineNumberTable;
import org.mozilla.javascript.interpreterv2.instruction.Instruction;

/**
 * InterpreterDataV2 holds the compiled instruction data for InterpreterV2. It extends JSCode to
 * integrate with the existing function object infrastructure.
 */
public final class InterpreterDataV2<T extends ScriptOrFn<T>> extends JSCode<T>
        implements Serializable {
    private static final long serialVersionUID = 5067677351589230235L;

    static final int INITIAL_STRINGTABLE_SIZE = 64;
    static final int INITIAL_NUMBERTABLE_SIZE = 64;
    static final int INITIAL_BIGINTTABLE_SIZE = 64;

    // Instruction array instead of bytecode
    final Instruction[] instructions;

    // String and number tables for constants
    final String[] stringTable;
    final double[] doubleTable;
    final BigInteger[] bigIntTable;

    // Nested functions
    final InterpreterDataV2<JSFunction>[] nestedFunctions;

    // Literals
    final Object[] regExpLiterals;
    final Object[] templateLiterals;
    final Object[] literalIds;

    // Exception handling
    final int[] exceptionTable;

    // Frame layout
    final int maxVars;
    final int maxLocals;
    final int maxStack;
    final int maxFrameArray;
    final int maxCalleeArgs;

    // Line number tracking
    final LineNumberTable lineNumberTable;

    // Long jumps for continuations
    final Map<Integer, Integer> longJumps;

    // First line PC
    final int firstLinePC;

    // CompilerData reference for metadata
    final CompilerData compilerData;

    InterpreterDataV2(
            Instruction[] instructions,
            String[] stringTable,
            double[] doubleTable,
            BigInteger[] bigIntTable,
            InterpreterDataV2<JSFunction>[] nestedFunctions,
            Object[] regExpLiterals,
            Object[] templateLiterals,
            Object[] literalIds,
            int[] exceptionTable,
            int maxVars,
            int maxLocals,
            int maxStack,
            int maxFrameArray,
            int maxCalleeArgs,
            LineNumberTable lineNumberTable,
            Map<Integer, Integer> longJumps,
            int firstLinePC,
            CompilerData compilerData) {
        this.instructions = instructions;
        this.stringTable = stringTable;
        this.doubleTable = doubleTable;
        this.bigIntTable = bigIntTable;
        this.nestedFunctions = nestedFunctions;
        this.regExpLiterals = regExpLiterals;
        this.templateLiterals = templateLiterals;
        this.literalIds = literalIds;
        this.exceptionTable = exceptionTable;
        this.maxVars = maxVars;
        this.maxLocals = maxLocals;
        this.maxStack = maxStack;
        this.maxFrameArray = maxFrameArray;
        this.maxCalleeArgs = maxCalleeArgs;
        this.lineNumberTable = lineNumberTable;
        this.longJumps = longJumps;
        this.firstLinePC = firstLinePC;
        this.compilerData = compilerData;
    }

    @Override
    public Object execute(
            Context cx,
            T executableObject,
            Object newTarget,
            Scriptable scope,
            Object thisObj,
            Object[] args) {
        // Call InterpreterV2.interpret when it's implemented
        // For now, throw an exception
        throw new UnsupportedOperationException("InterpreterV2 not yet implemented");
        // return InterpreterV2.interpret(executableObject, this, cx, scope, (Scriptable) thisObj,
        // args);
    }

    @Override
    public Object resume(
            Context cx,
            T executableObject,
            Object state,
            Scriptable scope,
            int operation,
            Object value) {
        // Call InterpreterV2.resumeGenerator when it's implemented
        // For now, throw an exception
        throw new UnsupportedOperationException(
                "InterpreterV2 generator resume not yet implemented");
        // return InterpreterV2.resumeGenerator(cx, scope, operation, state, value);
    }

    @Override
    public String toString() {
        return "InterpreterDataV2[" + (compilerData != null ? compilerData.name : "unknown") + "]";
    }

    /** Builder for InterpreterDataV2 following the upstream pattern. */
    public static class Builder<T extends ScriptOrFn<T>> extends JSCode.Builder<T> {
        Instruction[] instructions;
        String[] stringTable;
        double[] doubleTable;
        BigInteger[] bigIntTable;
        InterpreterDataV2<JSFunction>[] nestedFunctions;
        Object[] regExpLiterals;
        Object[] templateLiterals;
        Object[] literalIds;
        int[] exceptionTable;

        int maxVars;
        int maxLocals;
        int maxStack;
        int maxFrameArray;
        int maxCalleeArgs;

        LineNumberTable lineNumberTable;
        Map<Integer, Integer> longJumps;
        int firstLinePC = -1;

        CompilerData compilerData;

        InterpreterDataV2<T> built = null;

        public Builder() {
            stringTable = new String[INITIAL_STRINGTABLE_SIZE];
            doubleTable = new double[INITIAL_NUMBERTABLE_SIZE];
            bigIntTable = new BigInteger[INITIAL_BIGINTTABLE_SIZE];
        }

        @Override
        public InterpreterDataV2<T> build() {
            if (built != null) {
                return built;
            }

            built =
                    new InterpreterDataV2<>(
                            instructions,
                            stringTable,
                            doubleTable,
                            bigIntTable,
                            nestedFunctions,
                            regExpLiterals,
                            templateLiterals,
                            literalIds,
                            exceptionTable,
                            maxVars,
                            maxLocals,
                            maxStack,
                            maxFrameArray,
                            maxCalleeArgs,
                            lineNumberTable,
                            longJumps,
                            firstLinePC,
                            compilerData);

            return built;
        }

        public void setInstructions(Instruction[] instructions) {
            this.instructions = instructions;
        }

        public void setCompilerData(CompilerData compilerData) {
            this.compilerData = compilerData;
            // Copy relevant fields from CompilerData
            this.maxVars = compilerData.maxVars;
            this.maxLocals = compilerData.maxLocals;
            this.maxStack = compilerData.maxStack;
            this.maxFrameArray = compilerData.maxFrameSize;
            this.exceptionTable = compilerData.exceptionTable;
        }

        public void setLineNumberTable(LineNumberTable lineNumberTable) {
            this.lineNumberTable = lineNumberTable;
        }

        public void setStringTable(String[] stringTable) {
            this.stringTable = stringTable;
        }

        public void setDoubleTable(double[] doubleTable) {
            this.doubleTable = doubleTable;
        }
    }
}
