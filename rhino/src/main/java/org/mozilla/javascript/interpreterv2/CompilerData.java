package org.mozilla.javascript.interpreterv2;

import static org.mozilla.javascript.ast.FunctionNode.ARROW_FUNCTION;
import static org.mozilla.javascript.ast.FunctionNode.FUNCTION_EXPRESSION;
import static org.mozilla.javascript.ast.FunctionNode.FUNCTION_EXPRESSION_STATEMENT;
import static org.mozilla.javascript.ast.FunctionNode.FUNCTION_STATEMENT;

import java.io.PrintStream;
import java.util.List;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.InstructionArray;
import org.mozilla.javascript.InterpreterV2;
import org.mozilla.javascript.JSCode;
import org.mozilla.javascript.ScriptOrFn;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.config.RhinoConfig;
import org.mozilla.javascript.debug.DebuggableScript;
import org.mozilla.javascript.interpreterv2.instruction.Instruction;

public class CompilerData<T extends ScriptOrFn<T>> extends JSCode<T> implements DebuggableScript {

    static boolean shouldDumpInstructions = RhinoConfig.get("rhino.printICodeV2", false);

    public static final int EXCEPTION_TRY_START_SLOT = 0;
    public static final int EXCEPTION_TRY_END_SLOT = 1;
    public static final int EXCEPTION_HANDLER_SLOT = 2;
    public static final int EXCEPTION_TYPE_SLOT = 3;
    public static final int EXCEPTION_LOCAL_SLOT = 4;
    public static final int EXCEPTION_SCOPE_SLOT = 5;
    // SLOT_SIZE: space for try start/end, handler, start, handler type,
    //            exception local and scope local
    public static final int EXCEPTION_SLOT_SIZE = 6;

    public final String name;
    public final String sourceFile;
    public final boolean needsActivation;
    public final FunctionType functionType;

    public final CompilerData<org.mozilla.javascript.JSFunction>[] nestedFunctions;

    public final Instruction[] instructions;
    public final InstructionArray instructionsRef;

    public final int[] exceptionTable;

    public final int maxVars;
    public final int maxLocals;
    public final int maxStack;
    public final int maxFrameSize;

    public final String[] argNames;
    public final boolean[] constArgs;
    public final int argCount;
    public final boolean hasRestParams;
    public final boolean hasDefaultParams;
    public final boolean requiresArgumentsObject;

    final int sourceStart;
    final int sourceEnd;

    public final int languageVersion;

    /**
     * @see LineNumberTable
     */
    private final LineNumberTable lineNumberTable;

    public final boolean isStrict;
    public final boolean topLevel;
    public final boolean isES6Generator;
    public final boolean isShorthand;

    private final Builder<?> parentBuilder;

    public final boolean evalFlag;

    public final boolean declaredAsFunctionExpression;

    private CompilerData(Builder<T> b) {
        this.languageVersion = b.languageVersion;
        this.sourceFile = b.sourceFile;
        this.isStrict = b.isStrict;
        this.functionType = b.functionType;
        this.name = b.name;
        this.needsActivation = b.needsActivation;
        this.nestedFunctions = b.nestedFunctions;
        this.instructions = b.instructions;
        this.instructionsRef = b.instructionsRef;
        this.exceptionTable = b.exceptionTable;
        this.maxVars = b.maxVars;
        this.maxLocals = b.maxLocals;
        this.maxStack = b.maxStack;
        this.maxFrameSize = b.maxFrameSize;
        this.argNames = b.argNames;
        this.constArgs = b.constArgs;
        this.argCount = b.argCount;
        this.hasRestParams = b.hasRestParams;
        this.hasDefaultParams = b.hasDefaultParams;
        this.requiresArgumentsObject = b.requiresArgumentsObject;
        this.sourceStart = b.sourceStart;
        this.sourceEnd = b.sourceEnd;
        this.lineNumberTable = b.builtLineNumberTable;
        this.topLevel = b.topLevel;
        this.isES6Generator = b.isES6Generator;
        this.isShorthand = b.isShorthand;
        this.parentBuilder = b.parentBuilder;
        this.evalFlag = b.evalFlag;
        this.declaredAsFunctionExpression = b.declaredAsFunctionExpression;
    }

    public Instruction[] getInstructions() {
        if (instructions == null) {
            throw new IllegalStateException("Instructions not set");
        }
        return instructions;
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
        return argNames.length;
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
        return sourceFile != null && sourceFile.startsWith("_GeneratedScript_");
    }

    @Override
    public int[] getLineNumbers() {
        return LineNumberTable.getLineNumbers(lineNumberTable);
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
        return parentBuilder == null ? null : (DebuggableScript) parentBuilder.build();
    }

    public boolean getParamOrVarConst(int index) {
        return constArgs[index];
    }

    public String getSourceCode() {
        return null;
    }

    public enum FunctionType {
        Script,
        FunctionStatement,
        FunctionExpression,
        FunctionExpressionStatement,
        ArrowFunction;

        public int toOldFunctionType() {
            switch (this) {
                case Script:
                    // There is not constant for this one
                    return 0;
                case FunctionStatement:
                    return FUNCTION_STATEMENT;
                case FunctionExpression:
                    return 2;
                case FunctionExpressionStatement:
                    return FUNCTION_EXPRESSION_STATEMENT;
                case ArrowFunction:
                    return ARROW_FUNCTION;
                default:
                    throw new IllegalArgumentException();
            }
        }

        static FunctionType fromInt(int functionType) {
            switch (functionType) {
                case 0:
                    return Script;
                case FUNCTION_STATEMENT:
                    return FunctionStatement;
                case FUNCTION_EXPRESSION:
                    return FunctionExpression;
                case FUNCTION_EXPRESSION_STATEMENT:
                    return FunctionExpressionStatement;
                case ARROW_FUNCTION:
                    return ArrowFunction;
                default:
                    throw new IllegalArgumentException("Invalid function type: " + functionType);
            }
        }
    }

    public int getInstructionCount() {
        if (instructions == null) {
            return 0;
        }
        return instructions.length;
    }

    public boolean isScript() {
        return functionType == FunctionType.Script;
    }

    public void dumpInstructions(PrintStream out) {
        if (!shouldDumpInstructions) {
            return;
        }

        // Function header with metadata
        String functionName = name != null ? name : "<anonymous>";
        out.printf(
                "function %s [maxStack=%d, locals=%d, instructions=%d]:%n",
                functionName, maxStack, maxLocals, getInstructionCount());
        out.println("  line number table: " + getLineNumberTableForDebug());

        // Instructions with virtual register numbering
        if (instructions != null) {
            for (int pc = 0; pc < instructions.length; pc++) {
                out.printf("%%%-3d: %s%n", pc, instructions[pc].toDebugString());
            }
        }

        // Exception handlers (if any)
        if (hasExceptionHandlers()) {
            out.println();
            out.println("exception handlers:");
            dumpExceptionHandlers(out);
        }

        out.println();
        out.flush();
    }

    private boolean hasExceptionHandlers() {
        return exceptionTable != null && exceptionTable.length > 0;
    }

    private void dumpExceptionHandlers(PrintStream out) {
        if (exceptionTable == null) {
            return;
        }

        for (int i = 0; i < exceptionTable.length; i += EXCEPTION_SLOT_SIZE) {
            int tryStart = exceptionTable[i + EXCEPTION_TRY_START_SLOT];
            int tryEnd = exceptionTable[i + EXCEPTION_TRY_END_SLOT];
            int handler = exceptionTable[i + EXCEPTION_HANDLER_SLOT];
            int type = exceptionTable[i + EXCEPTION_TYPE_SLOT];
            int local = exceptionTable[i + EXCEPTION_LOCAL_SLOT];
            int scope = exceptionTable[i + EXCEPTION_SCOPE_SLOT];

            out.printf(
                    "  try %%%-3d..%%%-3d -> %%%-3d (type=%d, local=%d, scope=%d)%n",
                    tryStart, tryEnd, handler, type, local, scope);
        }
    }

    public int getFirstLineNumber() {
        return LineNumberTable.getFirstLineNumber(lineNumberTable);
    }

    public short getPcFirstLineNumber() {
        return LineNumberTable.getPcFirstLineNumber(lineNumberTable);
    }

    /**
     * Get the set of line numbers associated with a given PC.
     *
     * @param pc The program counter
     * @return List of line numbers, or null if PC has no associated lines
     */
    public List<Integer> getLineSetFromPc(int pc) {
        return LineNumberTable.getLineSetFromPc(lineNumberTable, pc);
    }

    public int getLineNumberFromPc(int pc) {
        return LineNumberTable.getLineNumberFromPc(lineNumberTable, pc);
    }

    public String getLineNumberTableForDebug() {
        return LineNumberTable.getDebugString(lineNumberTable);
    }

    @Override
    public Object execute(
            Context cx,
            T executableObject,
            Object newTarget,
            Scriptable scope,
            Object thisObj,
            Object[] args) {
        return InterpreterV2.interpret(
                executableObject, this, cx, scope, (Scriptable) thisObj, args);
    }

    @Override
    public Object resume(
            Context cx,
            T executableObject,
            Object state,
            Scriptable scope,
            int operation,
            Object value) {
        return InterpreterV2.resumeGenerator(cx, scope, operation, state, value);
    }

    @Override
    public String toString() {
        return sourceFile + ':' + name;
    }

    /**
     * Mutable working state for building an immutable {@link CompilerData}. Mirrors the builder
     * pattern used by {@link org.mozilla.javascript.InterpreterData.Builder}.
     */
    public static class Builder<T extends ScriptOrFn<T>> extends JSCode.Builder<T> {

        final int languageVersion;
        final String sourceFile;
        final Builder<?> parentBuilder;

        public String name;
        public boolean isStrict;
        public boolean needsActivation;
        public FunctionType functionType;
        public boolean topLevel;
        public boolean isES6Generator;
        public boolean isShorthand;
        public boolean requiresArgumentsObject;
        public boolean hasRestParams;
        public boolean hasDefaultParams;
        public boolean evalFlag;
        public boolean declaredAsFunctionExpression;

        public CompilerData<org.mozilla.javascript.JSFunction>[] nestedFunctions;

        public Instruction[] instructions;
        public InstructionArray instructionsRef;

        public int[] exceptionTable;

        public int maxVars;
        public int maxLocals;
        public int maxStack = 0;
        public int maxFrameSize;

        public String[] argNames;
        public boolean[] constArgs;
        public int argCount;

        public int sourceStart;
        public int sourceEnd;

        LineNumberTable builtLineNumberTable;

        private CompilerData<T> built;

        public Builder(
                int languageVersion,
                String sourceFile,
                boolean inStrictMode,
                FunctionType functionType) {
            this.languageVersion = languageVersion;
            this.sourceFile = sourceFile;
            this.isStrict = inStrictMode;
            this.functionType = functionType;
            this.parentBuilder = null;
        }

        public Builder(Builder<?> parent) {
            this.parentBuilder = parent;
            this.languageVersion = parent.languageVersion;
            this.sourceFile = parent.sourceFile;
            this.isStrict = parent.isStrict;
        }

        public void setFunctionTypeFromInt(int functionType) {
            this.functionType = FunctionType.fromInt(functionType);
        }

        public void setLineNumberTable(LineNumberTable.Builder lineNumberTableBuilder) {
            this.builtLineNumberTable = lineNumberTableBuilder.buildFinalTable();
        }

        @Override
        public CompilerData<T> build() {
            if (built == null) {
                built = new CompilerData<>(this);
            }
            return built;
        }
    }
}
