/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.interpreterv2;

import org.mozilla.javascript.debug.DebuggableScript;
import org.mozilla.javascript.interpreterv2.instruction.Instruction;

/**
 * Holds compiled instruction data for InterpreterV2.
 *
 * <p>This is a stub implementation that will be expanded in a later commit.
 */
public class CompilerData implements DebuggableScript {

    public String name;
    public String sourceFile;
    public boolean needsActivation = false;
    public FunctionType functionType;

    public CompilerData[] nestedFunctions;

    public Instruction[] instructions;

    public int[] exceptionTable;

    public int maxVars;
    public int maxLocals;
    public int maxStack = 0;
    public int maxFrameSize;

    public String[] argNames;
    public boolean[] constArgs;
    public int argCount;
    public boolean hasRestParams;
    public boolean hasDefaultParams;
    public boolean requiresArgumentsObject;

    public int languageVersion;

    public boolean isStrict;
    public boolean topLevel;
    public boolean isES6Generator;

    public boolean evalFlag;

    public boolean declaredAsFunctionExpression;

    public enum FunctionType {
        Script,
        FunctionStatement,
        FunctionExpression,
        FunctionExpressionStatement,
        ArrowFunction
    }

    @Override
    public boolean isTopLevel() {
        return topLevel;
    }

    @Override
    public boolean isFunction() {
        return functionType != FunctionType.Script;
    }

    @Override
    public String getFunctionName() {
        return name;
    }

    @Override
    public int getParamCount() {
        return argCount;
    }

    @Override
    public int getParamAndVarCount() {
        return argNames != null ? argNames.length : 0;
    }

    @Override
    public String getParamOrVarName(int index) {
        return argNames[index];
    }

    @Override
    public String getSourceName() {
        return sourceFile;
    }

    @Override
    public boolean isGeneratedScript() {
        return false;
    }

    @Override
    public int[] getLineNumbers() {
        return new int[0];
    }

    @Override
    public int getFunctionCount() {
        return nestedFunctions == null ? 0 : nestedFunctions.length;
    }

    @Override
    public DebuggableScript getFunction(int index) {
        return nestedFunctions[index];
    }

    @Override
    public DebuggableScript getParent() {
        return null;
    }

    public boolean getParamOrVarConst(int index) {
        return constArgs != null && constArgs[index];
    }
}
