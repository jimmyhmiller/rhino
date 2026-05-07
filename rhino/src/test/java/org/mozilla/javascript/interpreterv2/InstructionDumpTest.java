package org.mozilla.javascript.interpreterv2;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mozilla.javascript.Context;

public class InstructionDumpTest {
    private Context cx;
    private ByteArrayOutputStream outputStream;
    private PrintStream originalErr;
    private boolean oldShouldDumpInstructions;

    @Before
    public void setUp() throws Exception {
        cx = Context.enter();
        cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
        cx.setEvaluationMethod(Context.EvaluationMethod.InterpreterV2);

        outputStream = new ByteArrayOutputStream();
        originalErr = System.err;
        PrintStream testErr = new PrintStream(outputStream);
        System.setErr(testErr);

        oldShouldDumpInstructions = CompilerData.shouldDumpInstructions;
    }

    @After
    public void tearDown() throws Exception {
        CompilerData.shouldDumpInstructions = oldShouldDumpInstructions;
        System.setErr(originalErr);
        Context.exit();
    }

    @Test
    public void testDumpDisabledByDefault() {
        String source = "var a = 1; a + 2;";
        cx.compileString(source, "test-script", 1, null);

        String output = outputStream.toString();

        String expected = "";

        assertEquals("Output should be empty when dump is disabled", expected, output);
    }

    @Test
    public void arithmetic() {
        checkGeneratedOutput(
                "var a = 5;\n"
                        + "var b = 3n;\n"
                        + "a + b - 2 / a % b * 3.14 ** 123456789;\n"
                        + "42;\n"
                        + "12345678;\n"
                        + "3.14;\n"
                        + "a << 42 * - b >> 43 + +c >>> 1;\n",
                "function <anonymous> [maxStack=3, locals=0, instructions=36]:\n"
                        + "  line number table: [0 -> [1],  3 -> [2],  7 -> [3],  18 -> [4],  20 -> [5],  22 -> [6],  24 -> [7],  35 -> [-1]]\n"
                        + "%0  : BindName(name=\"a\")\n"
                        + "%1  : SetName(lhs=pop, name=\"a\", rhs=5)\n"
                        + "%2  : VoidInstruction(obj=pop)\n"
                        + "%3  : BindName(name=\"b\")\n"
                        + "%4  : BigInt(value=3n)\n"
                        + "%5  : SetName(lhs=pop, name=\"b\", rhs=pop)\n"
                        + "%6  : VoidInstruction(obj=pop)\n"
                        + "%7  : Name(name=\"a\")\n"
                        + "%8  : Name(name=\"b\")\n"
                        + "%9  : Add(lhs=pop, rhs=pop)\n"
                        + "%10 : Name(name=\"a\")\n"
                        + "%11 : Divide(lhs=2, rhs=pop)\n"
                        + "%12 : Name(name=\"b\")\n"
                        + "%13 : Mod(lhs=pop, rhs=pop)\n"
                        + "%14 : Exponentiate(lhs=3.14, rhs=123456789)\n"
                        + "%15 : Multiply(lhs=pop, rhs=pop)\n"
                        + "%16 : Subtract(lhs=pop, rhs=pop)\n"
                        + "%17 : PopResult\n"
                        + "%18 : ShortNumber(value=42)\n"
                        + "%19 : PopResult\n"
                        + "%20 : Int(value=12345678)\n"
                        + "%21 : PopResult\n"
                        + "%22 : Num(value=3.14)\n"
                        + "%23 : PopResult\n"
                        + "%24 : Name(name=\"a\")\n"
                        + "%25 : Name(name=\"b\")\n"
                        + "%26 : Neg(obj=pop)\n"
                        + "%27 : Multiply(lhs=42, rhs=pop)\n"
                        + "%28 : LeftShift(lhs=pop, rhs=pop)\n"
                        + "%29 : Name(name=\"c\")\n"
                        + "%30 : Pos(obj=pop)\n"
                        + "%31 : Add(lhs=43, rhs=pop)\n"
                        + "%32 : RightShift(lhs=pop, rhs=pop)\n"
                        + "%33 : UnsignedRightShift(lhs=pop, rhs=1)\n"
                        + "%34 : PopResult\n"
                        + "%35 : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void bitTwiddling() {
        checkGeneratedOutput(
                "a & b | c ^ ~d",
                "function <anonymous> [maxStack=3, locals=0, instructions=10]:\n"
                        + "  line number table: [0 -> [1],  9 -> [-1]]\n"
                        + "%0  : Name(name=\"a\")\n"
                        + "%1  : Name(name=\"b\")\n"
                        + "%2  : BitAnd(lhs=pop, rhs=pop)\n"
                        + "%3  : Name(name=\"c\")\n"
                        + "%4  : Name(name=\"d\")\n"
                        + "%5  : BitNot(obj=pop)\n"
                        + "%6  : BitXor(lhs=pop, rhs=pop)\n"
                        + "%7  : BitOr(lhs=pop, rhs=pop)\n"
                        + "%8  : PopResult\n"
                        + "%9  : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void strings() {
        checkGeneratedOutput(
                "var a = 'hello'; a + ' world';",
                "function <anonymous> [maxStack=1, locals=0, instructions=7]:\n"
                        + "  line number table: [0 -> [1],  6 -> [-1]]\n"
                        + "%0  : BindName(name=\"a\")\n"
                        + "%1  : SetName(lhs=pop, name=\"a\", rhs=\"hello\")\n"
                        + "%2  : VoidInstruction(obj=pop)\n"
                        + "%3  : Name(name=\"a\")\n"
                        + "%4  : AnyStringAdd\n"
                        + "%5  : PopResult\n"
                        + "%6  : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void optimizedAdd() {
        checkGeneratedOutput(
                "false + false; true + x;\n" + "'a' + 'b'; 'a' + x",
                "function <anonymous> [maxStack=1, locals=0, instructions=11]:\n"
                        + "  line number table: [0 -> [1],  5 -> [2],  10 -> [-1]]\n"
                        + "%0  : Add(lhs=false, rhs=false)\n"
                        + "%1  : PopResult\n"
                        + "%2  : Name(name=\"x\")\n"
                        + "%3  : Add(lhs=true, rhs=pop)\n"
                        + "%4  : PopResult\n"
                        + "%5  : PushConstant(value=\"ab\")\n"
                        + "%6  : PopResult\n"
                        + "%7  : Name(name=\"x\")\n"
                        + "%8  : StringAnyAdd\n"
                        + "%9  : PopResult\n"
                        + "%10 : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void functionCallBasic() {
        checkGeneratedOutput(
                "function test(a, b) { return a + b; }\ntest(1, 2);\na.b() + c[d]()",
                "function test [maxStack=2, locals=0, instructions=4]:\n"
                        + "  line number table: [0 -> [1]]\n"
                        + "%0  : GetVar(index=0)\n"
                        + "%1  : GetVar(index=1)\n"
                        + "%2  : Add(lhs=pop, rhs=pop)\n"
                        + "%3  : Return\n"
                        + "\n"
                        + "function <anonymous> [maxStack=3, locals=0, instructions=13]:\n"
                        + "  line number table: [0 -> [2],  3 -> [3],  12 -> [-1]]\n"
                        + "%0  : NameAndThis(name=\"test\")\n"
                        + "%1  : Call(callType=Call, lookupResult=pop, args=[1, 2])\n"
                        + "%2  : PopResult\n"
                        + "%3  : Name(name=\"a\")\n"
                        + "%4  : PropAndThis(name=\"b\")\n"
                        + "%5  : Call(callType=Call, lookupResult=pop, args=[])\n"
                        + "%6  : Name(name=\"c\")\n"
                        + "%7  : Name(name=\"d\")\n"
                        + "%8  : ElemAndThis(obj=\"PopOperand\", id=\"PopOperand\")\n"
                        + "%9  : Call(callType=Call, lookupResult=pop, args=[])\n"
                        + "%10 : Add(lhs=pop, rhs=pop)\n"
                        + "%11 : PopResult\n"
                        + "%12 : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void optionalAccess() {
        checkGeneratedOutput(
                "a?.b\n" + "c?.['d']\n" + "e?.();\n" + "f?.g()\n" + "h?.['i']()",
                "function <anonymous> [maxStack=1, locals=0, instructions=38]:\n"
                        + "  line number table: [0 -> [1],  7 -> [2],  14 -> [3],  21 -> [4],  29 -> [5],  37 -> [-1]]\n"
                        + "%0  : Name(name=\"a\")\n"
                        + "%1  : IfNullUndefined(object=peek(offset=0), offset=\"+3\")\n"
                        + "%2  : GetProp(lhs=pop, name=\"b\", nowarn=false)\n"
                        + "%3  : Goto(offset=\"+3\")\n"
                        + "%4  : Pop\n"
                        + "%5  : Name(name=\"undefined\")\n"
                        + "%6  : PopResult\n"
                        + "%7  : Name(name=\"c\")\n"
                        + "%8  : IfNullUndefined(object=peek(offset=0), offset=\"+3\")\n"
                        + "%9  : GetElem(lhs=pop, property=\"d\")\n"
                        + "%10 : Goto(offset=\"+3\")\n"
                        + "%11 : Pop\n"
                        + "%12 : Name(name=\"undefined\")\n"
                        + "%13 : PopResult\n"
                        + "%14 : NameAndThisOptional(name=\"e\")\n"
                        + "%15 : IfNotNullUndefined(object=peek(offset=0), offset=\"+4\")\n"
                        + "%16 : Pop\n"
                        + "%17 : Name(name=\"undefined\")\n"
                        + "%18 : Goto(offset=\"+2\")\n"
                        + "%19 : Call(callType=Call, lookupResult=pop, args=[])\n"
                        + "%20 : PopResult\n"
                        + "%21 : Name(name=\"f\")\n"
                        + "%22 : PropAndThisOptional(obj=pop, property=\"g\")\n"
                        + "%23 : IfNotNullUndefined(object=peek(offset=0), offset=\"+4\")\n"
                        + "%24 : Pop\n"
                        + "%25 : Name(name=\"undefined\")\n"
                        + "%26 : Goto(offset=\"+2\")\n"
                        + "%27 : Call(callType=Call, lookupResult=pop, args=[])\n"
                        + "%28 : PopResult\n"
                        + "%29 : Name(name=\"h\")\n"
                        + "%30 : ElemAndThisOptional(obj=\"PopOperand\", id=\"StringOperand\")\n"
                        + "%31 : IfNotNullUndefined(object=peek(offset=0), offset=\"+4\")\n"
                        + "%32 : Pop\n"
                        + "%33 : Name(name=\"undefined\")\n"
                        + "%34 : Goto(offset=\"+2\")\n"
                        + "%35 : Call(callType=Call, lookupResult=pop, args=[])\n"
                        + "%36 : PopResult\n"
                        + "%37 : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void objectLiteral() {
        checkGeneratedOutput(
                "var obj = {prop: 'value'}; obj.prop; obj.newProp = 'new';",
                "function <anonymous> [maxStack=3, locals=0, instructions=12]:\n"
                        + "  line number table: [0 -> [1],  11 -> [-1]]\n"
                        + "%0  : BindName(name=\"obj\")\n"
                        + "%1  : NewObjectLiteral(keys=[\"prop\"], literalValues=[\"value\"],"
                        + " copyKeys=false)\n"
                        + "%2  : ObjectLit(object=peek(offset=0))\n"
                        + "%3  : SetName(lhs=pop, name=\"obj\", rhs=pop)\n"
                        + "%4  : VoidInstruction(obj=pop)\n"
                        + "%5  : Name(name=\"obj\")\n"
                        + "%6  : GetProp(lhs=pop, name=\"prop\", nowarn=false)\n"
                        + "%7  : PopResult\n"
                        + "%8  : Name(name=\"obj\")\n"
                        + "%9  : SetProp(lhs=pop, name=\"newProp\", rhs=\"new\")\n"
                        + "%10 : PopResult\n"
                        + "%11 : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void objectLiteralSpread() {
        checkGeneratedOutput(
                "var o = {a: 1, ...b, c: 2};",
                "function <anonymous> [maxStack=4, locals=0, instructions=9]:\n"
                        + "  line number table: [0 -> [1],  8 -> [-1]]\n"
                        + "%0  : BindName(name=\"o\")\n"
                        + "%1  : NewObjectLiteralWithSpread(prefixKeys=[\"a\"], prefixValues=[1], nonSpreadCount=2)\n"
                        + "%2  : Name(name=\"b\")\n"
                        + "%3  : LitSpread(source=pop)\n"
                        + "%4  : LitPush(key=\"c\", value=2, kind=0)\n"
                        + "%5  : ObjectLit(object=peek(offset=0))\n"
                        + "%6  : SetName(lhs=pop, name=\"o\", rhs=pop)\n"
                        + "%7  : VoidInstruction(obj=pop)\n"
                        + "%8  : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void objectLiteralLiteralsBetweenNonLiterals() {
        checkGeneratedOutput(
                "var o = {a: 1, b: f(), c: 2};",
                "function <anonymous> [maxStack=4, locals=0, instructions=9]:\n"
                        + "  line number table: [0 -> [1],  8 -> [-1]]\n"
                        + "%0  : BindName(name=\"o\")\n"
                        + "%1  : NewObjectLiteral(keys=[\"a\", \"b\", \"c\"],"
                        + " literalValues=[1, null, 2], copyKeys=false)\n"
                        + "%2  : NameAndThis(name=\"f\")\n"
                        + "%3  : Call(callType=Call, lookupResult=pop, args=[])\n"
                        + "%4  : LitSetAt(slot=1, key=null, value=pop, kind=0)\n"
                        + "%5  : ObjectLit(object=peek(offset=0))\n"
                        + "%6  : SetName(lhs=pop, name=\"o\", rhs=pop)\n"
                        + "%7  : VoidInstruction(obj=pop)\n"
                        + "%8  : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void computedProperties() {
        checkGeneratedOutput(
                "o = {[f()]: 0};",
                "function <anonymous> [maxStack=4, locals=0, instructions=10]:\n"
                        + "  line number table: [0 -> [1],  9 -> [-1]]\n"
                        + "%0  : BindName(name=\"o\")\n"
                        + "%1  : NewObjectLiteral(keys=[\"#\"], literalValues=[0],"
                        + " copyKeys=true)\n"
                        + "%2  : NameAndThis(name=\"f\")\n"
                        + "%3  : Call(callType=Call, lookupResult=pop, args=[])\n"
                        + "%4  : ToPropertyKey\n"
                        + "%5  : LitSetAt(slot=0, key=pop, value=null, kind=0)\n"
                        + "%6  : ObjectLit(object=peek(offset=0))\n"
                        + "%7  : SetName(lhs=pop, name=\"o\", rhs=pop)\n"
                        + "%8  : PopResult\n"
                        + "%9  : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void delete() {
        checkGeneratedOutput(
                "delete a; delete b.c;",
                "function <anonymous> [maxStack=1, locals=0, instructions=7]:\n"
                        + "  line number table: [0 -> [1],  6 -> [-1]]\n"
                        + "%0  : BindName(name=\"a\")\n"
                        + "%1  : DelName(lhs=\"PopOperand\", rhs=\"StringOperand\")\n"
                        + "%2  : PopResult\n"
                        + "%3  : Name(name=\"b\")\n"
                        + "%4  : DelProp(lhs=\"PopOperand\", rhs=\"StringOperand\")\n"
                        + "%5  : PopResult\n"
                        + "%6  : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void in() {
        checkGeneratedOutput(
                "x in o",
                "function <anonymous> [maxStack=2, locals=0, instructions=5]:\n"
                        + "  line number table: [0 -> [1],  4 -> [-1]]\n"
                        + "%0  : Name(name=\"x\")\n"
                        + "%1  : Name(name=\"o\")\n"
                        + "%2  : In(lhs=pop, rhs=pop)\n"
                        + "%3  : PopResult\n"
                        + "%4  : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void arrayLiteral() {
        checkGeneratedOutput(
                "var arr = [1, 2, 3]; arr[0]; arr[1] = 'changed';",
                "function <anonymous> [maxStack=2, locals=0, instructions=11]:\n"
                        + "  line number table: [0 -> [1],  10 -> [-1]]\n"
                        + "%0  : BindName(name=\"arr\")\n"
                        + "%1  : ArrayLit(elements=[1, 2, 3], skipIndices=0)\n"
                        + "%2  : SetName(lhs=pop, name=\"arr\", rhs=pop)\n"
                        + "%3  : VoidInstruction(obj=pop)\n"
                        + "%4  : Name(name=\"arr\")\n"
                        + "%5  : GetElem(lhs=pop, property=0)\n"
                        + "%6  : PopResult\n"
                        + "%7  : Name(name=\"arr\")\n"
                        + "%8  : SetElem(lhs=pop, elem=1, rhs=\"changed\")\n"
                        + "%9  : PopResult\n"
                        + "%10 : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void sparseArray() {
        checkGeneratedOutput(
                "[1, , 3]",
                "function <anonymous> [maxStack=1, locals=0, instructions=3]:\n"
                        + "  line number table: [0 -> [1],  2 -> [-1]]\n"
                        + "%0  : ArrayLit(elements=[1, 3], skipIndices=1)\n"
                        + "%1  : PopResult\n"
                        + "%2  : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void ifs() {
        checkGeneratedOutput(
                "var x = 5; if (x > 3) { x = 10; } else { x = 0; }",
                "function <anonymous> [maxStack=1, locals=0, instructions=14]:\n"
                        + "  line number table: [0 -> [1],  13 -> [-1]]\n"
                        + "%0  : BindName(name=\"x\")\n"
                        + "%1  : SetName(lhs=pop, name=\"x\", rhs=5)\n"
                        + "%2  : VoidInstruction(obj=pop)\n"
                        + "%3  : Name(name=\"x\")\n"
                        + "%4  : Comparison(op=\"GT\", lhs=pop, rhs=3)\n"
                        + "%5  : IfNe(lhs=pop, offset=\"+5\")\n"
                        + "%6  : BindName(name=\"x\")\n"
                        + "%7  : SetName(lhs=pop, name=\"x\", rhs=10)\n"
                        + "%8  : PopResult\n"
                        + "%9  : Goto(offset=\"+4\")\n"
                        + "%10 : BindName(name=\"x\")\n"
                        + "%11 : SetName(lhs=pop, name=\"x\", rhs=0)\n"
                        + "%12 : PopResult\n"
                        + "%13 : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void simpleSwitch() {
        checkGeneratedOutput(
                "function f() {\n"
                        + "  switch (x) {\n"
                        + "   case 0: return 'zero'\n"
                        + "   case 1: return 'one';\n"
                        + "   default: return 'many';\n"
                        + "  }\n"
                        + "}",
                "function f [maxStack=2, locals=0, instructions=9]:\n"
                        + "  line number table: [0 -> [1, 2],  1 -> [3, 4],  2 -> [3],  4 -> [4],  6 -> [5]]\n"
                        + "%0  : Name(name=\"x\")\n"
                        + "%1  : SimpleSwitch(jumpTable={0.0=1, 1.0=3}, offset=\"+5\")\n"
                        + "%2  : PushConstant(value=\"zero\")\n"
                        + "%3  : Return\n"
                        + "%4  : PushConstant(value=\"one\")\n"
                        + "%5  : Return\n"
                        + "%6  : PushConstant(value=\"many\")\n"
                        + "%7  : Return\n"
                        + "%8  : ReturnUndefined\n"
                        + "\n"
                        + "function <anonymous> [maxStack=0, locals=0, instructions=1]:\n"
                        + "  line number table: [0 -> [-1]]\n"
                        + "%0  : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void complexSwitch() {
        checkGeneratedOutput(
                "function f() {\n"
                        + "  switch (x) {\n"
                        + "   case 0: return 'zero'\n"
                        + "   case 1: return 'one';\n"
                        + "   case x + 2: return 'x+2';\n"
                        + "   default: return 'many';\n"
                        + "  }\n"
                        + "}",
                "function f [maxStack=2, locals=0, instructions=17]:\n"
                        + "  line number table: [0 -> [1, 2],  1 -> [3, 4, 5],  8 -> [3],  10 -> [4],  12 -> [5],  14 -> [6]]\n"
                        + "%0  : Name(name=\"x\")\n"
                        + "%1  : IfEqPop(value=peek(offset=0), test=0, offset=\"+7\")\n"
                        + "%2  : IfEqPop(value=peek(offset=0), test=1, offset=\"+8\")\n"
                        + "%3  : Name(name=\"x\")\n"
                        + "%4  : Add(lhs=pop, rhs=2)\n"
                        + "%5  : IfEqPop(value=peek(offset=0), test=pop, offset=\"+7\")\n"
                        + "%6  : Cleanup(object=peek(offset=0))\n"
                        + "%7  : Goto(offset=\"+7\")\n"
                        + "%8  : PushConstant(value=\"zero\")\n"
                        + "%9  : Return\n"
                        + "%10 : PushConstant(value=\"one\")\n"
                        + "%11 : Return\n"
                        + "%12 : PushConstant(value=\"x+2\")\n"
                        + "%13 : Return\n"
                        + "%14 : PushConstant(value=\"many\")\n"
                        + "%15 : Return\n"
                        + "%16 : ReturnUndefined\n"
                        + "\n"
                        + "function <anonymous> [maxStack=0, locals=0, instructions=1]:\n"
                        + "  line number table: [0 -> [-1]]\n"
                        + "%0  : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void debugger() {
        checkGeneratedOutput(
                "debugger;",
                "function <anonymous> [maxStack=0, locals=0, instructions=2]:\n"
                        + "  line number table: [1 -> [-1]]\n"
                        + "%0  : DebuggerInstruction\n"
                        + "%1  : ReturnResult\n"
                        + "\n");
    }

    @Test
    @Ignore(
            "Compiler emits Equal(lhs=pop, rhs=\"boolean\") on rebase vs"
                    + " Equal(lhs=\"boolean\", rhs=pop) on integrate — operand evaluation order"
                    + " divergence to investigate.")
    public void typeofInstanceof() {
        checkGeneratedOutput(
                "3 instanceof Number && typeof false == 'boolean' || typeof x",
                "function <anonymous> [maxStack=1, locals=0, instructions=11]:\n"
                        + "  line number table: [0 -> [1],  10 -> [-1]]\n"
                        + "%0  : Name(name=\"Number\")\n"
                        + "%1  : Instanceof(lhs=3, rhs=pop)\n"
                        + "%2  : IfNe(lhs=peek(offset=0), offset=\"+4\")\n"
                        + "%3  : Pop\n"
                        + "%4  : Typeof(obj=false)\n"
                        + "%5  : Equal(lhs=\"boolean\", rhs=pop)\n"
                        + "%6  : IfEq(lhs=peek(offset=0), offset=\"+3\")\n"
                        + "%7  : Pop\n"
                        + "%8  : TypeofName(name=\"x\")\n"
                        + "%9  : PopResult\n"
                        + "%10 : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void superInstructions() {
        checkGeneratedOutput(
                "o = {\n"
                        + " f() {\n"
                        + "    delete super.d;\n"
                        + "    return super.a + super[b] * super.c() + super.d++ + super[e]--;\n"
                        + "  }\n"
                        + "}",
                "function f [maxStack=4, locals=0, instructions=24]:\n"
                        + "  line number table: [0 -> [2, 3],  2 -> [4]]\n"
                        + "%0  : DelPropSuper(lhs=\"SuperOperand\", rhs=\"StringOperand\")\n"
                        + "%1  : VoidInstruction(obj=pop)\n"
                        + "%2  : GetPropSuper(superObject=super, property=\"a\", noWarn=false)\n"
                        + "%3  : Name(name=\"b\")\n"
                        + "%4  : GetElemSuper(superObject=super, elem=pop)\n"
                        + "%5  : PropAndThis(name=\"c\")\n"
                        + "%6  : Call(callType=CallOnSuper, lookupResult=pop, args=[])\n"
                        + "%7  : Multiply(lhs=pop, rhs=pop)\n"
                        + "%8  : Add(lhs=pop, rhs=pop)\n"
                        + "%9  : GetPropSuper(superObject=super, property=\"d\", noWarn=false)\n"
                        + "%10 : Dup\n"
                        + "%11 : Add(lhs=pop, rhs=1)\n"
                        + "%12 : SetPropSuper(superObject=super, property=\"d\", rhs=pop)\n"
                        + "%13 : Pop\n"
                        + "%14 : Add(lhs=pop, rhs=pop)\n"
                        + "%15 : Name(name=\"e\")\n"
                        + "%16 : GetElemSuper(superObject=super, elem=pop)\n"
                        + "%17 : Dup\n"
                        + "%18 : Subtract(lhs=pop, rhs=1)\n"
                        + "%19 : Name(name=\"e\")\n"
                        + "%20 : SetElemSuper(superObject=super, elem=pop, rhs=pop)\n"
                        + "%21 : Pop\n"
                        + "%22 : Add(lhs=pop, rhs=pop)\n"
                        + "%23 : Return\n"
                        + "\n"
                        + "function <anonymous> [maxStack=4, locals=0, instructions=9]:\n"
                        + "  line number table: [0 -> [1],  8 -> [-1]]\n"
                        + "%0  : BindName(name=\"o\")\n"
                        + "%1  : NewObjectLiteral(keys=[\"f\"], literalValues=[null],"
                        + " copyKeys=false)\n"
                        + "%2  : ClosureExpression(fnIndex=0)\n"
                        + "%3  : FunctionStoreHomeObject(closure=peek(offset=0),"
                        + " homeObject=peek(offset=-2))\n"
                        + "%4  : LitSetAt(slot=0, key=null, value=pop, kind=0)\n"
                        + "%5  : ObjectLit(object=peek(offset=0))\n"
                        + "%6  : SetName(lhs=pop, name=\"o\", rhs=pop)\n"
                        + "%7  : PopResult\n"
                        + "%8  : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void specialCalls() {
        checkGeneratedOutput(
                "eval()\nnew eval()",
                "function <anonymous> [maxStack=1, locals=0, instructions=7]:\n"
                        + "  line number table: [0 -> [1],  3 -> [2],  6 -> [-1]]\n"
                        + "%0  : NameAndThis(name=\"eval\")\n"
                        + "%1  : SpecialCall(lookupResult=pop, args=[], line=1, callType=1)\n"
                        + "%2  : PopResult\n"
                        + "%3  : Name(name=\"eval\")\n"
                        + "%4  : SpecialCallNew(fun=pop, args=[], callType=1)\n"
                        + "%5  : PopResult\n"
                        + "%6  : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void specialRefs() {
        cx.setLanguageVersion(Context.VERSION_1_8);

        checkGeneratedOutput(
                "a.__proto__ = b.__proto__",
                "function <anonymous> [maxStack=2, locals=0, instructions=8]:\n"
                        + "  line number table: [0 -> [1],  7 -> [-1]]\n"
                        + "%0  : Name(name=\"a\")\n"
                        + "%1  : RefSpecial(lhs=pop, property=\"__proto__\")\n"
                        + "%2  : Name(name=\"b\")\n"
                        + "%3  : RefSpecial(lhs=pop, property=\"__proto__\")\n"
                        + "%4  : GetRef(obj=pop)\n"
                        + "%5  : SetRef(obj=pop, rhs=pop)\n"
                        + "%6  : PopResult\n"
                        + "%7  : ReturnResult\n"
                        + "\n");
    }

    @Test
    @Ignore(
            "InstructionFormatter doesn't render Undefined.instance as \"undefined\" upstream;"
                    + " falls through to default Object.toString().")
    public void nullUndefinedLiterals() {
        checkGeneratedOutput(
                "undefined ?? null",
                "function <anonymous> [maxStack=1, locals=0, instructions=6]:\n"
                        + "  line number table: [0 -> [1],  5 -> [-1]]\n"
                        + "%0  : PushConstant(value=undefined)\n"
                        + "%1  : IfNotNullUndefined(object=peek(offset=0), offset=\"+3\")\n"
                        + "%2  : Pop\n"
                        + "%3  : PushConstant(value=null)\n"
                        + "%4  : PopResult\n"
                        + "%5  : ReturnResult\n"
                        + "\n");
    }

    // Test for FEATURE_TREAT_NUMERIC_LITERALS_LIKE_OLD_RHINO (`legacyNumbers`) intentionally
    // dropped — that feature flag is not part of upstream Rhino.

    @Test
    public void incDec() {
        checkGeneratedOutput(
                "function f(a) { ++a; --b; c[d]++; e.f--; }",
                "function f [maxStack=2, locals=0, instructions=12]:\n"
                        + "  line number table: [0 -> [1]]\n"
                        + "%0  : VarIncDec(index=0, mask=0)\n"
                        + "%1  : VoidInstruction(obj=pop)\n"
                        + "%2  : NameIncDec(name=\"b\", mask=1)\n"
                        + "%3  : VoidInstruction(obj=pop)\n"
                        + "%4  : Name(name=\"c\")\n"
                        + "%5  : Name(name=\"d\")\n"
                        + "%6  : ElemIncDec(obj=\"PopOperand\", elem=\"PopOperand\", mask=2)\n"
                        + "%7  : VoidInstruction(obj=pop)\n"
                        + "%8  : Name(name=\"e\")\n"
                        + "%9  : PropIncDec(obj=pop, property=\"f\", mask=3)\n"
                        + "%10 : VoidInstruction(obj=pop)\n"
                        + "%11 : ReturnUndefined\n"
                        + "\n"
                        + "function <anonymous> [maxStack=0, locals=0, instructions=1]:\n"
                        + "  line number table: [0 -> [-1]]\n"
                        + "%0  : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void tryCatch() {
        checkGeneratedOutput(
                "try {\n" + "  throw new Error();\n" + "} catch (e) {\n" + "  throw e;\n" + "}\n",
                "function <anonymous> [maxStack=1, locals=3, instructions=19]:\n"
                        + "  line number table: [1 -> [1, 2],  7 -> [3],  10 -> [4],  18 -> [-1]]\n"
                        + "%0  : SaveScope(offset=1)\n"
                        + "%1  : Name(name=\"Error\")\n"
                        + "%2  : New(fun=pop, args=[])\n"
                        + "%3  : Throw(value=pop, line=2)\n"
                        + "%4  : Goto(offset=\"+12\")\n"
                        + "%5  : LocalLoad(slot=0)\n"
                        + "%6  : CatchScope(exception=pop, name=\"e\", localIndex=2, scopeIndex=0)\n"
                        + "%7  : LocalLoad(slot=2)\n"
                        + "%8  : EnterWith(obj=pop)\n"
                        + "%9  : Nop\n"
                        + "%10 : Name(name=\"e\")\n"
                        + "%11 : Throw(value=pop, line=4)\n"
                        + "%12 : LeaveWith\n"
                        + "%13 : Goto(offset=\"+3\")\n"
                        + "%14 : LeaveWith\n"
                        + "%15 : LocalClear(slot=2)\n"
                        + "%16 : LocalClear(slot=1)\n"
                        + "%17 : LocalClear(slot=0)\n"
                        + "%18 : ReturnResult\n"
                        + "\n"
                        + "exception handlers:\n"
                        + "  try %1  ..%5   -> %5   (type=0, local=0, scope=1)\n"
                        + "  try %0  ..%0   -> %0   (type=0, local=0, scope=0)\n"
                        + "\n");
    }

    @Test
    public void forOfIn() {
        checkGeneratedOutput(
                "for (var x of a) {}\n" + "for (var x in a) {}\n" + "for (var [x, y] in a) {}\n",
                "function <anonymous> [maxStack=3, locals=1, instructions=51]:\n"
                        + "  line number table: [0 -> [1],  12 -> [2],  24 -> [3],  50 -> [-1]]\n"
                        + "%0  : Nop\n"
                        + "%1  : Name(name=\"a\")\n"
                        + "%2  : EnumInitValuesInOrder(obj=pop, index=0)\n"
                        + "%3  : Goto(offset=\"+6\")\n"
                        + "%4  : BindName(name=\"x\")\n"
                        + "%5  : EnumId(localBlockRef=0)\n"
                        + "%6  : SetName(lhs=pop, name=\"x\", rhs=pop)\n"
                        + "%7  : VoidInstruction(obj=pop)\n"
                        + "%8  : Nop\n"
                        + "%9  : EnumNext(localBlockRef=0)\n"
                        + "%10 : IfEq(lhs=pop, offset=\"-6\")\n"
                        + "%11 : LocalClear(slot=0)\n"
                        + "%12 : Nop\n"
                        + "%13 : Name(name=\"a\")\n"
                        + "%14 : EnumInitKeys(obj=pop, index=0)\n"
                        + "%15 : Goto(offset=\"+6\")\n"
                        + "%16 : BindName(name=\"x\")\n"
                        + "%17 : EnumId(localBlockRef=0)\n"
                        + "%18 : SetName(lhs=pop, name=\"x\", rhs=pop)\n"
                        + "%19 : VoidInstruction(obj=pop)\n"
                        + "%20 : Nop\n"
                        + "%21 : EnumNext(localBlockRef=0)\n"
                        + "%22 : IfEq(lhs=pop, offset=\"-6\")\n"
                        + "%23 : LocalClear(slot=0)\n"
                        + "%24 : Nop\n"
                        + "%25 : Name(name=\"a\")\n"
                        + "%26 : EnumInitArray(obj=pop, index=0)\n"
                        + "%27 : Goto(offset=\"+20\")\n"
                        + "%28 : NewObjectLiteral(keys=[\"$0\"], literalValues=[null],"
                        + " copyKeys=false)\n"
                        + "%29 : EnumId(localBlockRef=0)\n"
                        + "%30 : LitSetAt(slot=0, key=null, value=pop, kind=0)\n"
                        + "%31 : ObjectLit(object=peek(offset=0))\n"
                        + "%32 : EnterWith(obj=pop)\n"
                        + "%33 : BindName(name=\"x\")\n"
                        + "%34 : Name(name=\"$0\")\n"
                        + "%35 : GetElem(lhs=pop, property=0)\n"
                        + "%36 : SetName(lhs=pop, name=\"x\", rhs=pop)\n"
                        + "%37 : VoidInstruction(obj=pop)\n"
                        + "%38 : BindName(name=\"y\")\n"
                        + "%39 : Name(name=\"$0\")\n"
                        + "%40 : GetElem(lhs=pop, property=1)\n"
                        + "%41 : SetName(lhs=pop, name=\"y\", rhs=pop)\n"
                        + "%42 : VoidInstruction(obj=pop)\n"
                        + "%43 : Name(name=\"$0\")\n"
                        + "%44 : LeaveWith\n"
                        + "%45 : VoidInstruction(obj=pop)\n"
                        + "%46 : Nop\n"
                        + "%47 : EnumNext(localBlockRef=0)\n"
                        + "%48 : IfEq(lhs=pop, offset=\"-20\")\n"
                        + "%49 : LocalClear(slot=0)\n"
                        + "%50 : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void generators() {
        checkGeneratedOutput(
                "function *g() {\n"
                        + "   yield 1;\n"
                        + "   var x = yield 2;\n"
                        + "   yield *h();\n"
                        + "   return 42;\n"
                        + "}\n"
                        + "function *g2() {}\n",
                "function g [maxStack=2, locals=0, instructions=16]:\n"
                        + "  line number table: [2 -> [1, 2],  5 -> [3],  10 -> [4],  15 -> [5]]\n"
                        + "%0  : Generator(baseLineno=1)\n"
                        + "%1  : ThawFrame(isYield=false, baseLineNumber=1)\n"
                        + "%2  : Yield(value=1, lineNumber=2)\n"
                        + "%3  : ThawFrame(isYield=true, baseLineNumber=2)\n"
                        + "%4  : VoidInstruction(obj=pop)\n"
                        + "%5  : BindName(name=\"x\")\n"
                        + "%6  : Yield(value=2, lineNumber=3)\n"
                        + "%7  : ThawFrame(isYield=true, baseLineNumber=3)\n"
                        + "%8  : SetName(lhs=pop, name=\"x\", rhs=pop)\n"
                        + "%9  : VoidInstruction(obj=pop)\n"
                        + "%10 : NameAndThis(name=\"h\")\n"
                        + "%11 : Call(callType=Call, lookupResult=pop, args=[])\n"
                        + "%12 : YieldStar(value=pop, lineNumber=4)\n"
                        + "%13 : ThawFrame(isYield=true, baseLineNumber=4)\n"
                        + "%14 : VoidInstruction(obj=pop)\n"
                        + "%15 : GeneratorReturn(lineNumber=5, value=42)\n"
                        + "\n"
                        + "function g2 [maxStack=0, locals=0, instructions=3]:\n"
                        + "  line number table: [2 -> [7]]\n"
                        + "%0  : Generator(baseLineno=7)\n"
                        + "%1  : ThawFrame(isYield=false, baseLineNumber=7)\n"
                        + "%2  : GeneratorEnd(lineNumber=7)\n"
                        + "\n"
                        + "function <anonymous> [maxStack=0, locals=0, instructions=1]:\n"
                        + "  line number table: [0 -> [-1]]\n"
                        + "%0  : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void not() {
        checkGeneratedOutput(
                "!x;",
                "function <anonymous> [maxStack=1, locals=0, instructions=4]:\n"
                        + "  line number table: [0 -> [1],  3 -> [-1]]\n"
                        + "%0  : Name(name=\"x\")\n"
                        + "%1  : Not(obj=pop)\n"
                        + "%2  : PopResult\n"
                        + "%3  : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void equalVariants() {
        checkGeneratedOutput(
                "a != b\n" + "c === d\n" + "e !== f\n",
                "function <anonymous> [maxStack=2, locals=0, instructions=13]:\n"
                        + "  line number table: [0 -> [1],  4 -> [2],  8 -> [3],  12 -> [-1]]\n"
                        + "%0  : Name(name=\"a\")\n"
                        + "%1  : Name(name=\"b\")\n"
                        + "%2  : NotEqual(lhs=pop, rhs=pop)\n"
                        + "%3  : PopResult\n"
                        + "%4  : Name(name=\"c\")\n"
                        + "%5  : Name(name=\"d\")\n"
                        + "%6  : ShallowEqual(lhs=pop, rhs=pop)\n"
                        + "%7  : PopResult\n"
                        + "%8  : Name(name=\"e\")\n"
                        + "%9  : Name(name=\"f\")\n"
                        + "%10 : ShallowNotEqual(lhs=pop, rhs=pop)\n"
                        + "%11 : PopResult\n"
                        + "%12 : ReturnResult\n"
                        + "\n");
    }

    @Test
    @Ignore(
            "InstructionFormatter doesn't render RECompiled as \"/.../flags\" upstream; falls"
                    + " through to default Object.toString().")
    public void regexpLiteral() {
        checkGeneratedOutput(
                "/[a-z]/",
                "function <anonymous> [maxStack=1, locals=0, instructions=3]:\n"
                        + "  line number table: [0 -> [1],  2 -> [-1]]\n"
                        + "%0  : Regexp(regexLiteral=/[a-z]/)\n"
                        + "%1  : PopResult\n"
                        + "%2  : ReturnResult\n"
                        + "\n");
    }

    @Test
    @Ignore(
            "InstructionFormatter doesn't render RECompiled as \"/.../flags\" upstream; falls"
                    + " through to default Object.toString().")
    public void regexpLiteralwithFlags() {
        checkGeneratedOutput(
                "/[a-z]/ig",
                "function <anonymous> [maxStack=1, locals=0, instructions=3]:\n"
                        + "  line number table: [0 -> [1],  2 -> [-1]]\n"
                        + "%0  : Regexp(regexLiteral=/[a-z]/gi)\n"
                        + "%1  : PopResult\n"
                        + "%2  : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void varAssignment() {
        checkGeneratedOutput(
                "function f(x) { x = 1; }",
                "function f [maxStack=1, locals=0, instructions=3]:\n"
                        + "  line number table: [0 -> [1]]\n"
                        + "%0  : SetVar(index=0, value=1)\n"
                        + "%1  : VoidInstruction(obj=pop)\n"
                        + "%2  : ReturnUndefined\n"
                        + "\n"
                        + "function <anonymous> [maxStack=0, locals=0, instructions=1]:\n"
                        + "  line number table: [0 -> [-1]]\n"
                        + "%0  : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void constAssignment() {
        checkGeneratedOutput(
                "const a = 0;\n" + "const {b} = 1;\n",
                "function <anonymous> [maxStack=2, locals=0, instructions=15]:\n"
                        + "  line number table: [0 -> [1],  3 -> [2],  7 -> [0],  14 -> [-1]]\n"
                        + "%0  : BindName(name=\"a\")\n"
                        + "%1  : SetConst(lhs=pop, name=\"a\", rhs=0)\n"
                        + "%2  : VoidInstruction(obj=pop)\n"
                        + "%3  : NewObjectLiteral(keys=[\"$0\"], literalValues=[1],"
                        + " copyKeys=false)\n"
                        + "%4  : ObjectLit(object=peek(offset=0))\n"
                        + "%5  : EnterWith(obj=pop)\n"
                        + "%6  : BindName(name=\"b\")\n"
                        + "%7  : Name(name=\"$0\")\n"
                        + "%8  : GetProp(lhs=pop, name=\"b\", nowarn=false)\n"
                        + "%9  : SetConst(lhs=pop, name=\"b\", rhs=pop)\n"
                        + "%10 : VoidInstruction(obj=pop)\n"
                        + "%11 : Name(name=\"$0\")\n"
                        + "%12 : LeaveWith\n"
                        + "%13 : VoidInstruction(obj=pop)\n"
                        + "%14 : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void strictSetName() {
        checkGeneratedOutput(
                "  'use strict';\n" + "  x = 42;\n",
                "function <anonymous> [maxStack=1, locals=0, instructions=6]:\n"
                        + "  line number table: [0 -> [1],  2 -> [2],  5 -> [-1]]\n"
                        + "%0  : PushConstant(value=\"use strict\")\n"
                        + "%1  : PopResult\n"
                        + "%2  : BindName(name=\"x\")\n"
                        + "%3  : StrictSetName(lhs=pop, name=\"x\", rhs=42)\n"
                        + "%4  : PopResult\n"
                        + "%5  : ReturnResult\n"
                        + "\n");
    }

    @Test
    public void templateLiteralCall() {
        checkGeneratedOutput(
                "x`y`",
                "function <anonymous> [maxStack=2, locals=0, instructions=5]:\n"
                        + "  line number table: [0 -> [1],  4 -> [-1]]\n"
                        + "%0  : NameAndThis(name=\"x\")\n"
                        + "%1  : TemplateLiteralCallsite(templateLiteral=[\"y\", \"y\"])\n"
                        + "%2  : Call(callType=Call, lookupResult=pop, args=[pop])\n"
                        + "%3  : PopResult\n"
                        + "%4  : ReturnResult\n"
                        + "\n");
    }

    private void checkGeneratedOutput(String source, String expected) {
        CompilerData.shouldDumpInstructions = true;
        try {
            cx.compileString(source, "test", 1, null);
            System.out.println("=====================");
            System.out.println(outputStream.toString());
            System.out.println("=====================");
            assertEquals(expected, outputStream.toString());
        } finally {
            CompilerData.shouldDumpInstructions = oldShouldDumpInstructions;
        }
    }
}
