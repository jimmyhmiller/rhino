package org.mozilla.javascript.interpreterv2.instruction;

import static org.mozilla.javascript.InterpreterV2.INVOCATION_COST;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Constructable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.JSFunction;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.interpreterv2.CompilerData;
import org.mozilla.javascript.interpreterv2.InstructionFormatter;
import org.mozilla.javascript.interpreterv2.operand.Operand;

public class New implements Instruction {
    private final Operand fun;
    private final Operand[] arguments;

    public New(Operand fun, Operand[] arguments) {
        this.fun = fun;
        this.arguments = arguments;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;

        if (cx.instructionThreshold != 0) {
            cx.instructionCount += INVOCATION_COST;
        }

        // TODO(Cam):
        //  Need to implement all the edge cases. Mostly with continuations

        Object[] args = frame.getArguments(cx, arguments);
        var lhs = fun.retrieveAndWrap(cx, frame);

        if (lhs instanceof JSFunction
                && ((JSFunction) lhs).getDescriptor().getCode() instanceof CompilerData) {
            // Note: this is a _very_ weird thing that we do in InterpreterV1. It's technically
            // incorrect, because we are using the correct meta/scope _only_ for the creation of the
            // object, and not for the execution of the constructor function, but we have to keep
            // this around for backward compatibility.
            JSFunction f = (JSFunction) lhs;
            if (frame.fnOrScript.getDescriptor().getSecurityDomain()
                    == f.getDescriptor().getSecurityDomain()) {
                if (cx.getLanguageVersion() >= Context.VERSION_ES6 && f.getHomeObject() != null) {
                    // Only methods have home objects associated with them
                    throw ScriptRuntime.typeErrorById("msg.not.ctor", f.getFunctionName());
                }
                if (f.getDescriptor().getConstructor() == null) {
                    // Arrow functions and generators are not constructors
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
        return InstructionFormatter.formatInstruction(this, "fun", fun, "args", arguments);
    }
}
