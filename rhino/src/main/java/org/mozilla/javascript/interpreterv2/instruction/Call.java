/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.interpreterv2.operand.Operand;

public class Call implements Instruction {
    // Cost added to instruction count for invocation operations
    private static final int INVOCATION_COST = 100;

    public enum Type {
        Call,
        CallOnSuper,
        TailCall,
        RefCall,
    }

    private final Operand lookupResult;
    private final Operand[] arguments;
    private final Type callType;

    public Call(Operand lookupResult, Operand[] arguments, Type callType) {
        this.lookupResult = lookupResult;
        this.arguments = arguments;
        this.callType = callType;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;

        if (cx.getInstructionObserverThreshold() != 0) {
            ScriptRuntime.addInstructionCount(cx, INVOCATION_COST);
        }

        // TODO: Need to implement all the edge cases. Mostly with continuations

        // CALL generation ensures that fun and funThisObj
        // are already Scriptable and Callable objects respectively
        Object[] args = frame.getArguments(cx, arguments);
        var result = (ScriptRuntime.LookupResult) lookupResult.retrieve(cx, frame);
        Scriptable funThisObj = result.getThis();
        Callable fun = result.getCallable();
        Scriptable funHomeObj =
                (fun instanceof BaseFunction) ? ((BaseFunction) fun).getHomeObject() : null;
        if (callType == Type.CallOnSuper) {
            // funThisObj would have been the "super" object, which we
            // used to lookup the function. Now that that's done, we
            // discard it and invoke the function with the current
            // "this".
            funThisObj = frame.thisObj;
        }

        if (callType == Type.RefCall) {
            frame.push(ScriptRuntime.callRef(fun, funThisObj, args, cx));
            return;
        }
        Scriptable calleeScope = frame.scope;
        if (frame.useActivation) {
            // SNC change to preserve native call scope when doing apply or call.
            calleeScope = ScriptableObject.getTopLevelScope(frame.scope);
        }

        // Note: lastInterpreterFrame is package-private, skip for simplified version
        frame.push(fun.call(cx, calleeScope, funThisObj, args));
    }

    @Override
    public int stackChange() {
        int count = 0;
        for (var argument : arguments) {
            count += argument.stackChange();
        }
        return 1 + lookupResult.stackChange() + count;
    }

    @Override
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Call{callType=").append(callType);
        sb.append(", lookupResult=");
        lookupResult.appendDebugString(sb);
        sb.append(", args=[");
        for (int i = 0; i < arguments.length; i++) {
            if (i > 0) sb.append(", ");
            arguments[i].appendDebugString(sb);
        }
        sb.append("]}");
        return sb.toString();
    }
}
