/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import org.mozilla.javascript.interpreterv2.CompilerData;

/**
 * InterpretedFunctionV2 is the function object used by InterpreterV2.
 *
 * <p>This is a stub implementation that will be expanded in a later commit.
 */
public class InterpretedFunctionV2 extends NativeFunction implements Script {

    public CompilerData compilerData;
    public InterpreterDataV2<?> idata;

    public InterpretedFunctionV2(CompilerData compilerData) {
        this.compilerData = compilerData;
    }

    public InterpretedFunctionV2(InterpreterDataV2<?> idata) {
        this.idata = idata;
        this.compilerData = idata.compilerData;
    }

    @Override
    public String getFunctionName() {
        return compilerData != null ? compilerData.getFunctionName() : "";
    }

    @Override
    protected int getLanguageVersion() {
        return compilerData != null ? compilerData.languageVersion : Context.VERSION_DEFAULT;
    }

    @Override
    protected int getParamCount() {
        return compilerData != null ? compilerData.getParamCount() : 0;
    }

    @Override
    protected int getParamAndVarCount() {
        return compilerData != null ? compilerData.getParamAndVarCount() : 0;
    }

    @Override
    protected String getParamOrVarName(int index) {
        return compilerData != null ? compilerData.getParamOrVarName(index) : null;
    }

    @Override
    protected boolean getParamOrVarConst(int index) {
        return compilerData != null && compilerData.getParamOrVarConst(index);
    }

    @Override
    public boolean isStrict() {
        return compilerData != null && compilerData.isStrict;
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        throw new UnsupportedOperationException("Stub implementation");
    }

    @Override
    public Object exec(Context cx, Scriptable scope) {
        return exec(cx, scope, scope);
    }

    @Override
    public Object exec(Context cx, Scriptable scope, Scriptable thisObj) {
        return call(cx, scope, thisObj, ScriptRuntime.emptyArgs);
    }
}
