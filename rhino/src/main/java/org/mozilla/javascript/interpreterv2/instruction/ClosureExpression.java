package org.mozilla.javascript.interpreterv2.instruction;

import static org.mozilla.javascript.InterpreterV2.getCurrentFrameHomeObject;

import org.mozilla.javascript.CallFrameV2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.JSFunction;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.interpreterv2.CompilerData;
import org.mozilla.javascript.interpreterv2.InstructionFormatter;

public class ClosureExpression implements Instruction {
    private final int fnIndex;

    public ClosureExpression(int fnIndex) {
        this.fnIndex = fnIndex;
    }

    @Override
    public void interpret(Context cx, CallFrameV2 frame) {
        frame.pc += 1;

        CompilerData<?> data = frame.compilerData;
        var fdata = data.nestedFunctions[fnIndex];
        boolean isArrow = fdata.functionType == CompilerData.FunctionType.ArrowFunction;
        Scriptable lexicalThis = isArrow ? frame.thisObj : null;
        Scriptable homeObject = isArrow ? getCurrentFrameHomeObject(frame) : null;

        JSFunction fn =
                JSFunction.createFunction(
                        cx,
                        frame.scope,
                        frame.fnOrScript.getDescriptor(),
                        fnIndex,
                        lexicalThis,
                        homeObject);
        frame.push(fn);
    }

    @Override
    public int stackChange() {
        return 1;
    }

    @Override
    public String toDebugString() {
        return InstructionFormatter.formatInstruction(this, "fnIndex", fnIndex);
    }
}
