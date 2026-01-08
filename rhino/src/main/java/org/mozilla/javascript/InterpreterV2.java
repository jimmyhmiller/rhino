/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import static org.mozilla.javascript.UniqueTag.DOUBLE_MARK;

import java.math.BigInteger;
import org.mozilla.javascript.ast.ScriptNode;
import org.mozilla.javascript.interpreterv2.CompilerData;
import org.mozilla.javascript.interpreterv2.instruction.Instruction;
import org.mozilla.javascript.interpreterv2.instruction.JumpInstruction;
import org.mozilla.javascript.interpreterv2.operand.Operand;

/**
 * InterpreterV2 is the instruction-based interpreter for Rhino.
 *
 * <p>This is a stub implementation that will be expanded in a later commit.
 */
public class InterpreterV2 implements Evaluator {

    // Cost added to instruction count for invocation operations
    public static final int INVOCATION_COST = 100;

    /** Main entry point for interpreting a function or script. */
    public static Object interpret(
            InterpretedFunctionV2 ifun,
            InterpreterDataV2<?> idata,
            Context cx,
            Scriptable scope,
            Scriptable thisObj,
            Object[] args) {

        // Create a minimal call frame using the default constructor
        // The CallFrameV2 constructor sets the final fields with default values
        CallFrameV2 frame = new CallFrameV2();
        frame.fnOrScript = ifun;
        frame.compilerData = idata.compilerData;
        frame.scope = scope;
        frame.thisObj = thisObj;
        frame.pc = 0;

        // Initialize the stack
        int stackSize = idata.maxVars + idata.maxLocals + idata.maxStack;
        frame.stack = new Object[stackSize];
        frame.stackAttributes = new int[stackSize];
        frame.doubleStack = new double[stackSize];
        frame.stackTop = idata.maxVars + idata.maxLocals - 1;

        // Copy arguments
        int argCount = Math.min(args.length, idata.maxVars);
        System.arraycopy(args, 0, frame.stack, 0, argCount);

        // Initialize undefined parameters
        for (int i = argCount; i < idata.maxVars; i++) {
            frame.stack[i] = Undefined.instance;
        }

        frame.result = Undefined.instance;
        frame.varSource = frame;
        frame.frozen = false;

        return interpretLoop(cx, frame, null);
    }

    /** Main interpreter loop. */
    private static Object interpretLoop(Context cx, CallFrameV2 frame, Object throwable) {
        // Get InterpreterDataV2 from the function object
        // For now, we need to get it differently since it's not directly in compilerData
        InterpreterDataV2<?> idata = frame.fnOrScript.idata;
        Instruction[] instructions = idata.instructions;

        // Main interpreter loop
        while (frame.pc < instructions.length) {
            try {
                Instruction instruction = instructions[frame.pc];

                // Save previous branch PC for debugging
                if (instruction instanceof JumpInstruction) {
                    frame.pcPrevBranch = frame.pc;
                }

                // Execute the instruction
                instruction.interpret(cx, frame);

                // Check for instruction count threshold
                if (cx.instructionCount > cx.instructionThreshold) {
                    cx.observeInstructionCount(cx.instructionCount);
                    cx.instructionCount = 0;
                }

            } catch (JavaScriptException jse) {
                // For now, just propagate JavaScript exceptions
                throw jse;
            } catch (Throwable ex) {
                // Wrap other exceptions as JavaScript exceptions
                throw new JavaScriptException(ex, null, 0);
            }
        }

        // Return the result
        return frame.result;
    }

    /** Resume a generator (stub for now). */
    public static Object resumeGenerator(
            Context cx, Scriptable scope, int operation, Object state, Object value) {
        throw new UnsupportedOperationException("Generator support not yet implemented");
    }

    /**
     * Get the home object for the current frame (for super property access).
     *
     * @param frame The current call frame
     * @return The home object, or null if none
     */
    public static Scriptable getCurrentFrameHomeObject(CallFrameV2 frame) {
        // Stub implementation - will be expanded later
        return null;
    }

    /**
     * Initialize a function in the given scope.
     *
     * @param cx The context
     * @param scope The scope
     * @param parent The parent function
     * @param index The function index
     */
    public static void initFunction(
            Context cx, Scriptable scope, InterpretedFunctionV2 parent, int index) {
        CompilerData nestedData = parent.compilerData.nestedFunctions[index];
        InterpretedFunctionV2 fn = new InterpretedFunctionV2(nestedData);
        // TODO: Properly initialize nested function when InterpreterDataV2 supports nested
        // functions
        fn.setParentScope(scope);
        fn.setPrototype(ScriptableObject.getFunctionPrototype(scope));
    }

    /**
     * Perform shallow equality comparison (===).
     *
     * @param cx The context
     * @param frame The current call frame
     * @param left The left operand
     * @param right The right operand
     * @return true if strictly equal
     */
    public static boolean doShallowEquals(
            Context cx, CallFrameV2 frame, Operand left, Operand right) {
        Object rhs;
        double rDouble = 0.0;
        if (right.isDouble(frame)) {
            rDouble = right.retrieveDouble(frame);
            rhs = DOUBLE_MARK;
        } else {
            rhs = right.retrieve(cx, frame);
        }
        Object lhs;
        double lDouble = 0.0;
        if (left.isDouble(frame)) {
            lDouble = left.retrieveDouble(frame);
            lhs = DOUBLE_MARK;
        } else {
            lhs = left.retrieve(cx, frame);
        }
        if (rhs == DOUBLE_MARK) {
            if (lhs instanceof Number && !(lhs instanceof BigInteger)) {
                lDouble = ((Number) lhs).doubleValue();
            } else if (lhs != DOUBLE_MARK) {
                return false;
            }
        } else if (lhs == DOUBLE_MARK) {
            if (rhs instanceof Number && !(rhs instanceof BigInteger)) {
                rDouble = ((Number) rhs).doubleValue();
            } else {
                return false;
            }
        } else {
            return ScriptRuntime.shallowEq(lhs, rhs);
        }
        return (lDouble == rDouble);
    }

    /**
     * Perform equality comparison (==).
     *
     * @param cx The context
     * @param frame The current call frame
     * @param left The left operand
     * @param right The right operand
     * @return true if equal
     */
    public static boolean doEquals(Context cx, CallFrameV2 frame, Operand left, Operand right) {
        Object rhs;
        double rDouble = 0.0;
        if (right.isDouble(frame)) {
            rDouble = right.retrieveDouble(frame);
            rhs = DOUBLE_MARK;
        } else {
            rhs = right.retrieve(cx, frame);
        }
        Object lhs;
        double lDouble = 0.0;
        if (left.isDouble(frame)) {
            lDouble = left.retrieveDouble(frame);
            lhs = DOUBLE_MARK;
        } else {
            lhs = left.retrieve(cx, frame);
        }
        if (rhs == DOUBLE_MARK) {
            if (lhs == DOUBLE_MARK) {
                return (lDouble == rDouble);
            }
            return ScriptRuntime.eqNumber(rDouble, lhs);
        }
        if (lhs == DOUBLE_MARK) {
            return ScriptRuntime.eqNumber(lDouble, rhs);
        }
        return ScriptRuntime.eq(lhs, rhs);
    }

    @Override
    public void captureStackInfo(RhinoException ex) {
        // Stub
    }

    @Override
    public String getSourcePositionFromStack(Context cx, int[] linep) {
        return null;
    }

    @Override
    public String getPatchedStack(RhinoException ex, String nativeStackTrace) {
        return null;
    }

    @Override
    public java.util.List<String> getScriptStack(RhinoException ex) {
        return java.util.Collections.emptyList();
    }

    @Override
    public void setEvalScriptFlag(Script script) {
        // Stub
    }

    @Override
    public Object compile(
            CompilerEnvirons compilerEnv,
            ScriptNode tree,
            String encodedSource,
            boolean returnFunction) {
        CompilerV2 compiler = new CompilerV2();
        return compiler.compile(compilerEnv, tree, encodedSource, returnFunction);
    }

    @Override
    public Script createScriptObject(Object bytecode, Object staticSecurityDomain) {
        InterpreterDataV2<?> idata = (InterpreterDataV2<?>) bytecode;
        return new InterpretedFunctionV2(idata);
    }

    @Override
    public Function createFunctionObject(
            Context cx, Scriptable scope, Object bytecode, Object staticSecurityDomain) {
        InterpreterDataV2<?> idata = (InterpreterDataV2<?>) bytecode;
        InterpretedFunctionV2 fn = new InterpretedFunctionV2(idata);
        fn.setParentScope(scope);
        fn.setPrototype(ScriptableObject.getFunctionPrototype(scope));
        return fn;
    }
}
