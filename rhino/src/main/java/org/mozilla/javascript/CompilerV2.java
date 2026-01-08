/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.Jump;
import org.mozilla.javascript.ast.ScriptNode;
import org.mozilla.javascript.interpreterv2.CompilerData;
import org.mozilla.javascript.interpreterv2.LineNumberTable;
import org.mozilla.javascript.interpreterv2.instruction.*;
import org.mozilla.javascript.interpreterv2.operand.*;

/**
 * Compiler for InterpreterV2 that transforms AST/IR into instruction arrays. This is a simplified
 * implementation that will be expanded as more instructions are added.
 */
public class CompilerV2 {

    private CompilerEnvirons compilerEnv;
    private ScriptNode scriptOrFn;
    private boolean isTopLevel;

    private List<Instruction> instructions;
    private int currentPc;

    // Jump tracking
    private Set<Integer> jumpTargets;
    private List<Jump> loopEnds;
    private List<Node> loopStarts;

    // Line number tracking
    private LineNumberTable.Builder lineNumberTableBuilder;

    // Constant pools
    private List<String> strings;
    private List<Double> doubles;

    // Local variable management
    private int maxLocals;
    private int maxStack;

    public CompilerV2() {
        this.instructions = new ArrayList<>();
        this.jumpTargets = new HashSet<>();
        this.strings = new ArrayList<>();
        this.doubles = new ArrayList<>();
        this.loopEnds = new ArrayList<>();
        this.loopStarts = new ArrayList<>();
        this.lineNumberTableBuilder = new LineNumberTable.Builder();
    }

    /** Compile a script or function node into InterpreterDataV2. */
    public <T extends ScriptOrFn<T>> InterpreterDataV2<T> compile(
            CompilerEnvirons compilerEnv,
            ScriptNode scriptOrFn,
            String encodedSource,
            boolean returnFunction) {

        this.compilerEnv = compilerEnv;
        this.scriptOrFn = scriptOrFn;
        this.isTopLevel = scriptOrFn.getFunctionCount() == 0 && scriptOrFn.getRegexpCount() == 0;

        // Transform the tree for compilation
        new NodeTransformer().transform(scriptOrFn, compilerEnv);

        // Generate instructions from the IR tree
        if (scriptOrFn instanceof FunctionNode) {
            generateFunctionICode((FunctionNode) scriptOrFn);
        } else {
            generateScriptICode((AstRoot) scriptOrFn);
        }

        // Build the InterpreterDataV2
        InterpreterDataV2.Builder<T> builder = new InterpreterDataV2.Builder<>();

        // Set instructions
        builder.setInstructions(instructions.toArray(new Instruction[0]));

        // Set constant tables
        if (!strings.isEmpty()) {
            builder.setStringTable(strings.toArray(new String[0]));
        }
        if (!doubles.isEmpty()) {
            builder.setDoubleTable(doubles.stream().mapToDouble(Double::doubleValue).toArray());
        }

        // Create and set CompilerData
        CompilerData compilerData = createCompilerData(scriptOrFn);
        builder.setCompilerData(compilerData);

        // Set line number table
        LineNumberTable lineNumberTable = lineNumberTableBuilder.buildFinalTable();
        if (lineNumberTable != null) {
            builder.setLineNumberTable(lineNumberTable);
        }

        // Build and return
        return builder.build();
    }

    private void generateFunctionICode(FunctionNode fn) {
        // Add function prologue
        if (fn.isGenerator()) {
            addInstruction(new Nop()); // Placeholder for generator support
        }

        // Generate body
        Node body = fn.getBody();
        if (body != null) {
            generateICode(body);
        }

        // Add implicit return if needed
        if (!endsWithReturn()) {
            addInstruction(ReturnUndefined.instance);
        }
    }

    private void generateScriptICode(AstRoot ast) {
        // Generate body
        Node body = ast.getFirstChild();
        while (body != null) {
            generateICode(body);
            body = body.getNext();
        }

        // Add implicit return for scripts
        if (!endsWithReturn()) {
            addInstruction(ReturnUndefined.instance);
        }
    }

    private void generateICode(Node node) {
        if (node == null) return;

        int type = node.getType();
        Node child = node.getFirstChild();

        switch (type) {
            case Token.RETURN:
                if (child != null) {
                    generateICode(child);
                    addInstruction(Return.instance);
                } else {
                    addInstruction(ReturnUndefined.instance);
                }
                break;

            case Token.EXPR_VOID:
            case Token.EXPR_RESULT:
                generateICode(child);
                if (type == Token.EXPR_VOID) {
                    addInstruction(Pop.instance);
                } else {
                    addInstruction(PopResult.instance);
                }
                break;

            case Token.GETVAR:
                int varIndex = node.getIntProp(Node.VARIABLE_PROP, -1);
                if (varIndex >= 0) {
                    addInstruction(new GetVar(varIndex));
                }
                break;

            case Token.SETVAR:
                varIndex = node.getIntProp(Node.VARIABLE_PROP, -1);
                if (varIndex >= 0) {
                    generateICode(child); // Generate value
                    addInstruction(new SetVar(varIndex, PopOperand.instance));
                }
                break;

            case Token.NAME:
                String name = node.getString();
                if (name != null) {
                    addInstruction(new Name(name));
                }
                break;

            case Token.SETNAME:
                name = node.getString();
                generateICode(child); // Generate value
                addInstruction(new SetName(PopOperand.instance, name, PopOperand.instance));
                break;

            case Token.GETPROP:
                generateICode(child); // Object
                String propName = child.getNext().getString();
                addInstruction(new GetProp(PopOperand.instance, propName, false));
                break;

            case Token.SETPROP:
                Node obj = child;
                Node prop = obj.getNext();
                Node value = prop.getNext();
                generateICode(obj);
                generateICode(value);
                addInstruction(
                        new SetProp(PopOperand.instance, prop.getString(), PopOperand.instance));
                break;

            case Token.GETELEM:
                generateICode(child); // Object
                generateICode(child.getNext()); // Index
                addInstruction(new GetElem(PopOperand.instance, PopOperand.instance));
                break;

            case Token.SETELEM:
                obj = child;
                Node index = obj.getNext();
                value = index.getNext();
                generateICode(obj);
                generateICode(index);
                generateICode(value);
                addInstruction(
                        new SetElem(PopOperand.instance, PopOperand.instance, PopOperand.instance));
                break;

            case Token.CALL:
            case Token.NEW:
                generateCallOrNew(node, type == Token.NEW);
                break;

            case Token.THIS:
                addInstruction(This.instance);
                break;

            case Token.NUMBER:
                double num = node.getDouble();
                addInstruction(new Num(num));
                break;

            case Token.STRING:
                String str = node.getString();
                int strIndex = addString(str);
                addInstruction(new PushConstant(strIndex));
                break;

            case Token.NULL:
                addInstruction(new PushConstant(null));
                break;

            case Token.TRUE:
                addInstruction(new PushConstant(Boolean.TRUE));
                break;

            case Token.FALSE:
                addInstruction(new PushConstant(Boolean.FALSE));
                break;

            case Token.VOID:
                generateICode(child);
                addInstruction(new VoidInstruction(PopOperand.instance));
                break;

            case Token.TYPEOF:
                generateICode(child);
                addInstruction(new Typeof(PopOperand.instance));
                break;

            case Token.NOT:
                generateICode(child);
                addInstruction(new Not(PopOperand.instance));
                break;

            case Token.NEG:
                generateICode(child);
                addInstruction(new Neg(PopOperand.instance));
                break;

            case Token.ADD:
                generateBinaryOp(
                        child, child.getNext(), new Add(PopOperand.instance, PopOperand.instance));
                break;

            case Token.SUB:
                generateBinaryOp(
                        child,
                        child.getNext(),
                        new Subtract(PopOperand.instance, PopOperand.instance));
                break;

            case Token.MUL:
                generateBinaryOp(
                        child,
                        child.getNext(),
                        new Multiply(PopOperand.instance, PopOperand.instance));
                break;

            case Token.DIV:
                generateBinaryOp(
                        child,
                        child.getNext(),
                        new Divide(PopOperand.instance, PopOperand.instance));
                break;

            case Token.MOD:
                generateBinaryOp(
                        child, child.getNext(), new Mod(PopOperand.instance, PopOperand.instance));
                break;

            case Token.BITOR:
                generateBinaryOp(
                        child,
                        child.getNext(),
                        new BitOr(PopOperand.instance, PopOperand.instance));
                break;

            case Token.BITAND:
                generateBinaryOp(
                        child,
                        child.getNext(),
                        new BitAnd(PopOperand.instance, PopOperand.instance));
                break;

            case Token.BITXOR:
                generateBinaryOp(
                        child,
                        child.getNext(),
                        new BitXor(PopOperand.instance, PopOperand.instance));
                break;

            case Token.LSH:
                generateBinaryOp(
                        child,
                        child.getNext(),
                        new LeftShift(PopOperand.instance, PopOperand.instance));
                break;

            case Token.RSH:
                generateBinaryOp(
                        child,
                        child.getNext(),
                        new RightShift(PopOperand.instance, PopOperand.instance));
                break;

            case Token.URSH:
                generateBinaryOp(
                        child,
                        child.getNext(),
                        new UnsignedRightShift(PopOperand.instance, PopOperand.instance));
                break;

            case Token.EQ:
                generateBinaryOp(
                        child,
                        child.getNext(),
                        new Equal(PopOperand.instance, PopOperand.instance));
                break;

            case Token.NE:
                generateBinaryOp(
                        child,
                        child.getNext(),
                        new NotEqual(PopOperand.instance, PopOperand.instance));
                break;

            case Token.LT:
            case Token.LE:
            case Token.GT:
            case Token.GE:
                generateComparison(node, type);
                break;

            case Token.SHEQ:
                generateBinaryOp(
                        child,
                        child.getNext(),
                        new ShallowEqual(PopOperand.instance, PopOperand.instance));
                break;

            case Token.SHNE:
                generateBinaryOp(
                        child,
                        child.getNext(),
                        new ShallowNotEqual(PopOperand.instance, PopOperand.instance));
                break;

            case Token.IN:
                generateBinaryOp(
                        child, child.getNext(), new In(PopOperand.instance, PopOperand.instance));
                break;

            case Token.INSTANCEOF:
                generateBinaryOp(
                        child,
                        child.getNext(),
                        new Instanceof(PopOperand.instance, PopOperand.instance));
                break;

            case Token.IFNE:
                generateIfJump(node, child);
                break;

            case Token.IFEQ:
                generateIfJump(node, child);
                break;

            case Token.GOTO:
                generateGoto(node);
                break;

            case Token.BLOCK:
                while (child != null) {
                    generateICode(child);
                    child = child.getNext();
                }
                break;

            case Token.ARRAYLIT:
                generateArrayLiteral(node);
                break;

            case Token.OBJECTLIT:
                generateObjectLiteral(node);
                break;

            default:
                // For unsupported operations, generate children and add a Nop
                while (child != null) {
                    generateICode(child);
                    child = child.getNext();
                }
                break;
        }
    }

    private void generateBinaryOp(Node left, Node right, Instruction op) {
        generateICode(left);
        generateICode(right);
        addInstruction(op);
    }

    private void generateComparison(Node node, int type) {
        Node left = node.getFirstChild();
        Node right = left.getNext();
        generateICode(left);
        generateICode(right);

        // Comparison uses Token type directly, not an enum
        addInstruction(new Comparison(PopOperand.instance, type, PopOperand.instance));
    }

    private void generateCallOrNew(Node node, boolean isNew) {
        Node child = node.getFirstChild();

        // Generate target
        generateICode(child);

        // Generate arguments
        List<Operand> argOperands = new ArrayList<>();
        Node arg = child.getNext();
        while (arg != null) {
            generateICode(arg);
            argOperands.add(PopOperand.instance);
            arg = arg.getNext();
        }

        if (isNew) {
            addInstruction(new New(PopOperand.instance, argOperands.toArray(new Operand[0])));
        } else {
            // Call constructor expects lookupResult, arguments, callType
            // For simplified version, using PopOperand as a placeholder for lookupResult
            addInstruction(
                    new Call(
                            PopOperand.instance,
                            argOperands.toArray(new Operand[0]),
                            Call.Type.Call));
        }
    }

    private void generateIfJump(Node node, Node condition) {
        generateICode(condition);

        JumpInstruction jump;
        if (node.getType() == Token.IFEQ) {
            jump = new IfEq(PopOperand.instance);
        } else {
            jump = new IfNe(PopOperand.instance);
        }

        addInstruction(jump);

        // Jump target will be resolved later
        if (node instanceof Jump) {
            Jump jumpNode = (Jump) node;
            // Store jump for later resolution
        }
    }

    private void generateGoto(Node node) {
        Goto gotoInst = new Goto();
        addInstruction(gotoInst);

        if (node instanceof Jump) {
            Jump jumpNode = (Jump) node;
            // Store jump for later resolution
        }
    }

    private void generateArrayLiteral(Node node) {
        List<Operand> elements = new ArrayList<>();
        Node child = node.getFirstChild();

        while (child != null) {
            generateICode(child);
            elements.add(PopOperand.instance);
            child = child.getNext();
        }

        addInstruction(new ArrayLit(elements.toArray(new Operand[0]), null));
    }

    private void generateObjectLiteral(Node node) {
        // Simplified object literal generation
        Object[] ids = (Object[]) node.getProp(Node.OBJECT_IDS_PROP);
        if (ids == null) {
            ids = new Object[0];
        }

        List<Operand> values = new ArrayList<>();
        Node child = node.getFirstChild();

        while (child != null) {
            generateICode(child);
            values.add(PopOperand.instance);
            child = child.getNext();
        }

        // ObjectLit simplified - using PopOperand instead of StackOperand
        addInstruction(
                new ObjectLit(
                        PopOperand.instance, null, values.toArray(new Operand[0]), ids, false));
    }

    private void addInstruction(Instruction inst) {
        instructions.add(inst);
        currentPc++;
    }

    private int addString(String str) {
        int index = strings.indexOf(str);
        if (index < 0) {
            index = strings.size();
            strings.add(str);
        }
        return index;
    }

    private boolean endsWithReturn() {
        if (instructions.isEmpty()) {
            return false;
        }
        Instruction last = instructions.get(instructions.size() - 1);
        return last instanceof Return
                || last instanceof ReturnUndefined
                || last instanceof ReturnResult;
    }

    private CompilerData createCompilerData(ScriptNode scriptOrFn) {
        CompilerData data = new CompilerData();

        data.name =
                scriptOrFn instanceof FunctionNode
                        ? ((FunctionNode) scriptOrFn).getName()
                        : "<script>";
        data.sourceFile = scriptOrFn.getSourceName();

        if (scriptOrFn instanceof FunctionNode) {
            FunctionNode fn = (FunctionNode) scriptOrFn;
            data.functionType =
                    fn.getFunctionType() == FunctionNode.ARROW_FUNCTION
                            ? CompilerData.FunctionType.ArrowFunction
                            : CompilerData.FunctionType.FunctionExpression;
        } else {
            data.functionType = CompilerData.FunctionType.Script;
        }

        data.instructions = instructions.toArray(new Instruction[0]);
        data.maxVars = scriptOrFn.getParamAndVarCount();
        data.maxLocals = maxLocals;
        data.maxStack = maxStack;
        data.argCount = scriptOrFn.getParamCount();
        data.isStrict = scriptOrFn.isInStrictMode();
        // TODO: Handle ES6 generators when supported
        data.isES6Generator = false; // scriptOrFn.isES6Generator();

        return data;
    }

    // Stub instruction for unsupported operations
    static class Nop implements Instruction {
        @Override
        public void interpret(Context cx, CallFrameV2 frame) {
            frame.pc += 1;
        }

        @Override
        public int stackChange() {
            return 0;
        }
    }

    // Stub for numeric constants
    static class Num implements Instruction {
        private final double value;

        Num(double value) {
            this.value = value;
        }

        @Override
        public void interpret(Context cx, CallFrameV2 frame) {
            frame.push(value);
            frame.pc += 1;
        }

        @Override
        public int stackChange() {
            return 1;
        }
    }

    // Stub for constants
    static class PushConstant implements Instruction {
        private final Object value;

        PushConstant(Object value) {
            this.value = value;
        }

        @Override
        public void interpret(Context cx, CallFrameV2 frame) {
            frame.push(value);
            frame.pc += 1;
        }

        @Override
        public int stackChange() {
            return 1;
        }
    }

    // Missing comparison instructions
    static class ShallowEqual implements Instruction {
        private final Operand lhs, rhs;

        ShallowEqual(Operand lhs, Operand rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        public void interpret(Context cx, CallFrameV2 frame) {
            Object left = lhs.retrieve(cx, frame);
            Object right = rhs.retrieve(cx, frame);
            frame.push(ScriptRuntime.shallowEq(left, right));
            frame.pc += 1;
        }

        @Override
        public int stackChange() {
            return 1 + lhs.stackChange() + rhs.stackChange();
        }
    }

    static class ShallowNotEqual implements Instruction {
        private final Operand lhs, rhs;

        ShallowNotEqual(Operand lhs, Operand rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        public void interpret(Context cx, CallFrameV2 frame) {
            Object left = lhs.retrieve(cx, frame);
            Object right = rhs.retrieve(cx, frame);
            frame.push(!ScriptRuntime.shallowEq(left, right));
            frame.pc += 1;
        }

        @Override
        public int stackChange() {
            return 1 + lhs.stackChange() + rhs.stackChange();
        }
    }
}
