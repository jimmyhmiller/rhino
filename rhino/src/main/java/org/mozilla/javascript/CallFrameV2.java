/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import static org.mozilla.javascript.UniqueTag.DOUBLE_MARK;

import org.mozilla.javascript.debug.DebuggableScript;
import org.mozilla.javascript.interpreterv2.CompilerData;
import org.mozilla.javascript.interpreterv2.operand.Operand;

/**
 * CallFrameV2 represents an activation frame for InterpreterV2.
 *
 * <p>This is a stub implementation that will be expanded in a later commit.
 */
public class CallFrameV2 implements ICallFrame {

    public CallFrameV2 parentFrame;
    public int frameIndex;
    public boolean frozen;

    public InterpretedFunctionV2 fnOrScript;
    public CompilerData compilerData;

    public Object[] stack;
    public int[] stackAttributes;
    public double[] doubleStack;

    public Object result;
    public double resultDbl;
    public int pc;
    public int stackTop = -1;
    public Scriptable scope;

    public Scriptable thisObj;

    public final boolean useActivation;
    public CallFrameV2 varSource;
    public final int localShift;
    public final int emptyStackTop;
    public Object throwable;
    public int pcPrevBranch;

    // Additional fields for future generator support
    public boolean isContinuationsTopFrame;
    public boolean shouldYieldToParent;

    /** Minimal constructor for stub implementation. */
    public CallFrameV2() {
        this.useActivation = false;
        this.localShift = 0;
        this.emptyStackTop = -1;
        this.varSource = this;
    }

    public void push(Object val) {
        stackTop += 1;
        stack[stackTop] = val;
    }

    public void push(int val) {
        stackTop += 1;
        stack[stackTop] = val;
    }

    public void push(double val) {
        stackTop += 1;
        stack[stackTop] = DOUBLE_MARK;
        doubleStack[stackTop] = val;
    }

    public void push(Object val, double doubleVal) {
        stackTop += 1;
        stack[stackTop] = val;
        doubleStack[stackTop] = doubleVal;
    }

    public void popResult() {
        result = stack[stackTop];
        resultDbl = doubleStack[stackTop];
        stack[stackTop] = null;
        stackTop -= 1;
    }

    public Object pop() {
        var value = stack[stackTop];
        stack[stackTop] = null;
        stackTop -= 1;
        return value;
    }

    public double popDouble() {
        var value = doubleStack[stackTop];
        stack[stackTop] = null;
        stackTop -= 1;
        return value;
    }

    public Object peek(int offset) {
        return stack[stackTop + offset];
    }

    public Object peek() {
        return peek(0);
    }

    public double peekDouble(int offset) {
        return doubleStack[stackTop + offset];
    }

    public double peekDouble() {
        return peekDouble(0);
    }

    public boolean isStackEmpty() {
        return stackTop == emptyStackTop;
    }

    public Object getVarAndWrap(int index) {
        var value = getVar(index);
        if (value == DOUBLE_MARK) {
            return ScriptRuntime.wrapNumber(getVarDouble(index));
        }
        return value;
    }

    public Object getVar(int index) {
        return varSource.stack[index];
    }

    public boolean isVarDouble(int index) {
        return getVar(index) == DOUBLE_MARK;
    }

    public double getVarDouble(int index) {
        return varSource.doubleStack[index];
    }

    public void setVar(int index, Object value) {
        varSource.stack[index] = value;
    }

    public void setVar(int index, double value) {
        varSource.stack[index] = DOUBLE_MARK;
        varSource.doubleStack[index] = value;
    }

    public void setVar(int index, Object value, double doubleValue) {
        varSource.stack[index] = value;
        varSource.doubleStack[index] = doubleValue;
    }

    public int getVarAttribute(int index) {
        return varSource.stackAttributes[index];
    }

    public void setVarAttribute(int index, int attributes) {
        varSource.stackAttributes[index] &= ~attributes;
    }

    public Object getLocal(int index) {
        return stack[localShift + index];
    }

    public double getLocalDouble(int index) {
        return doubleStack[localShift + index];
    }

    public void setLocal(int index, Object value) {
        stack[localShift + index] = value;
    }

    public void setResult(Object value) {
        result = value;
    }

    public void setResult(double value) {
        result = DOUBLE_MARK;
        resultDbl = value;
    }

    public Object[] getArguments(Context cx, Operand[] arguments) {
        if (arguments.length == 0) {
            return ScriptRuntime.emptyArgs;
        }
        Object[] args = new Object[arguments.length];
        for (int i = arguments.length - 1; i >= 0; i--) {
            args[i] = arguments[i].retrieveAndWrap(cx, this);
        }
        return args;
    }

    public void popN(int n) {
        for (int i = 0; i < n; i++) {
            pop();
        }
    }

    public void saveExceptionScope(int exceptionIndex, Scriptable scope) {
        stack[localShift + exceptionIndex] = scope;
    }

    public void saveSubRoutineReturnPC(int returnPcOffset, double subRoutineReturnPC) {
        stack[localShift + returnPcOffset] = subRoutineReturnPC;
    }

    public boolean hasSubRoutineReturnPC(int returnPcOffset) {
        Object value = stack[localShift + returnPcOffset];
        return value instanceof Double;
    }

    public double getSubRoutineReturnPC(int returnPcOffset) {
        if (!hasSubRoutineReturnPC(returnPcOffset)) {
            throw new IllegalStateException("Use hasSubRoutineReturnPC first");
        }
        return (Double) stack[localShift + returnPcOffset];
    }

    @Override
    public int getFrameIndex() {
        return frameIndex;
    }

    @Override
    public ICallFrame getParentFrame() {
        return parentFrame;
    }

    @Override
    public int getPcSourceLineStart() {
        return pc;
    }

    @Override
    public DebuggableScript getData() {
        return compilerData;
    }
}
