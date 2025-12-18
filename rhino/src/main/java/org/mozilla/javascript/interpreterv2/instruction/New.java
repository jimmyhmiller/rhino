/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.interpreterv2.instruction;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Constructable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.InterpretedFunctionV2;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.interpreterv2.operand.Operand;

public class New implements Instruction {
    // Cost added to instruction count for invocation operations
    private static final int INVOCATION_COST = 100;

    private final Operand fun;
    private final Operand[] arguments;

    public New(Operand fun, Operand[] arguments) {
        this.fun = fun;
        this.arguments = arguments;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;

        if (cx.getInstructionObserverThreshold() != 0) {
            ScriptRuntime.addInstructionCount(cx, INVOCATION_COST);
        }

        // TODO: Need to implement all the edge cases. Mostly with continuations

        Object[] args = frame.getArguments(cx, arguments);
        var lhs = fun.retrieveAndWrap(cx, frame);

        if (lhs instanceof InterpretedFunctionV2) {
            // Note: this is a _very_ weird thing that we do in InterpreterV1. It's technically
            // incorrect, because we are using the correct meta/scope _only_ for the creation of the
            // object, and not for the execution of the constructor function, but we have to keep
            // this around for backward compatibility.
            InterpretedFunctionV2 f = (InterpretedFunctionV2) lhs;
            // Simplified security domain check - assume same domain for now
            if (true) {
                if (cx.getLanguageVersion() >= Context.VERSION_ES6 && f.getHomeObject() != null) {
                    // Only methods have home objects associated with them
                    throw ScriptRuntime.typeErrorById("msg.not.ctor", f.getFunctionName());
                }

                Scriptable newInstance = f.createObject(cx, frame.scope);
                Object result = f.call(cx, frame.scope, newInstance, args);
                if (ScriptRuntime.isObject(result)) {
                    frame.push(result);
                } else {
                    frame.push(newInstance);
                }
                return;
            }
        }

        Scriptable frameScope = frame.scope;
        if (!(lhs instanceof Constructable)) {
            throw ScriptRuntime.notFunctionError(lhs);
        }
        Constructable ctor = (Constructable) lhs;

        Scriptable newInstance = ctor.construct(cx, frameScope, args);

        // Note: setCreatedByRhino() method not available in this version

        frame.push(newInstance);
    }

    @Override
    public int stackChange() {
        int count = 0;
        for (var argument : arguments) {
            count += argument.stackChange();
        }
        return 1 + fun.stackChange() + count;
    }

    @Override
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("New{fun=");
        fun.appendDebugString(sb);
        sb.append(", args=[");
        for (int i = 0; i < arguments.length; i++) {
            if (i > 0) sb.append(", ");
            arguments[i].appendDebugString(sb);
        }
        sb.append("]}");
        return sb.toString();
    }
}
