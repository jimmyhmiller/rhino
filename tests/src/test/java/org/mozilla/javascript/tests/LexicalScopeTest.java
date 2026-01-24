/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.testutils.Utils;

/**
 * Tests for ES6 lexical scoping (let/const) including: - Temporal Dead Zone (TDZ) - Block scoping -
 * Const immutability - Per-iteration loop bindings - Redeclaration errors
 */
public class LexicalScopeTest {

    // ===================== TDZ (Temporal Dead Zone) Tests =====================

    @Test
    public void tdzAccessLetBeforeDeclaration() {
        Utils.assertException(
                Context.VERSION_ES6,
                EcmaError.class,
                "ReferenceError: Cannot access 'x' before initialization",
                "{ x; let x = 1; }");
    }

    @Test
    public void tdzAccessConstBeforeDeclaration() {
        Utils.assertException(
                Context.VERSION_ES6,
                EcmaError.class,
                "ReferenceError: Cannot access 'x' before initialization",
                "{ x; const x = 1; }");
    }

    @Test
    public void tdzLetWithoutInitializerIsUndefined() {
        // After declaration without initializer, let should be undefined (not TDZ)
        Utils.assertWithAllModes_ES6(true, "{ let x; x === undefined; }");
    }

    @Test
    public void tdzShadowingOuterVariable() {
        // Inner let shadows outer, accessing before inner declaration should throw
        Utils.assertException(
                Context.VERSION_ES6,
                EcmaError.class,
                "ReferenceError: Cannot access 'x' before initialization",
                "let x = 1; { x; let x = 2; }");
    }

    @Test
    @org.junit.jupiter.api.Disabled("Script-level TDZ for self-referential let not yet implemented")
    public void tdzSelfReference() {
        // let x = x; should throw because x is in TDZ
        Utils.assertException(
                Context.VERSION_ES6,
                EcmaError.class,
                "ReferenceError: Cannot access 'x' before initialization",
                "let x = x;");
    }

    @Test
    @org.junit.jupiter.api.Disabled("Script-level TDZ for closure-before-init not yet implemented")
    public void tdzClosureCalledBeforeInit() {
        // Function accessing let variable before it's initialized
        Utils.assertException(
                Context.VERSION_ES6,
                EcmaError.class,
                "ReferenceError: Cannot access 'x' before initialization",
                "let f = function() { return x; }; f(); let x = 1;");
    }

    @Test
    public void tdzClosureCalledAfterInit() {
        // Function accessing let variable after it's initialized should work
        Utils.assertWithAllModes_ES6(1.0, "let f = function() { return x; }; let x = 1; f();");
    }

    // ===================== Block Scoping Tests =====================

    @Test
    public void letIsBlockScoped() {
        Utils.assertWithAllModes_ES6("undefined", "{ let x = 1; } typeof x;");
    }

    @Test
    public void constIsBlockScoped() {
        Utils.assertWithAllModes_ES6("undefined", "{ const x = 1; } typeof x;");
    }

    @Test
    public void letShadowsOuterVar() {
        Utils.assertWithAllModes_ES6(2.0, "var x = 1; { let x = 2; x; }");
    }

    @Test
    public void letDoesNotAffectOuterVar() {
        Utils.assertWithAllModes_ES6(1.0, "var x = 1; { let x = 2; } x;");
    }

    @Test
    public void nestedBlocksEachHaveOwnLet() {
        Utils.assertWithAllModes_ES6(3.0, "let x = 1; { let x = 2; { let x = 3; x; } }");
    }

    @Test
    public void letInForLoopIsBlockScoped() {
        Utils.assertWithAllModes_ES6("undefined", "for (let i = 0; i < 1; i++) {} typeof i;");
    }

    // ===================== Const Immutability Tests =====================

    @Test
    public void constCannotBeReassigned() {
        Utils.assertException(
                Context.VERSION_ES6, EcmaError.class, "TypeError", "const x = 1; x = 2;");
    }

    @Test
    public void constObjectPropertiesCanBeModified() {
        Utils.assertWithAllModes_ES6(1.0, "const o = {}; o.x = 1; o.x;");
    }

    // ===================== Per-Iteration Loop Binding Tests =====================

    @Test
    public void classicClosureInLoopTest() {
        // Each iteration should have its own binding
        Utils.assertWithAllModes_ES6(
                "[0,1,2]",
                "var funcs = [];\n"
                        + "for (let i = 0; i < 3; i++) {\n"
                        + "    funcs.push(function() { return i; });\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(function(f) { return f(); }));");
    }

    @Test
    public void forOfClosureTest() {
        Utils.assertWithAllModes_ES6(
                "[1,2,3]",
                "var funcs = [];\n"
                        + "for (let x of [1,2,3]) {\n"
                        + "    funcs.push(function() { return x; });\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(function(f) { return f(); }));");
    }

    @Test
    public void forInClosureTest() {
        Utils.assertWithAllModes_ES6(
                "[\"a\",\"b\"]",
                "var funcs = [];\n"
                        + "var obj = {a:1, b:2};\n"
                        + "for (let k in obj) {\n"
                        + "    funcs.push(function() { return k; });\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(function(f) { return f(); }));");
    }

    @Test
    public void letInLoopWithContinue() {
        Utils.assertWithAllModes_ES6(
                "[0,2]",
                "var funcs = [];\n"
                        + "for (let i = 0; i < 3; i++) {\n"
                        + "    if (i === 1) continue;\n"
                        + "    funcs.push(function() { return i; });\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(function(f) { return f(); }));");
    }

    @Test
    public void letInLoopWithBreak() {
        Utils.assertWithAllModes_ES6(
                "[0]",
                "var funcs = [];\n"
                        + "for (let i = 0; i < 3; i++) {\n"
                        + "    funcs.push(function() { return i; });\n"
                        + "    if (i === 0) break;\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(function(f) { return f(); }));");
    }

    // ===================== Redeclaration Tests =====================

    @Test
    public void cannotRedeclareLetWithLet() {
        Utils.assertException(
                Context.VERSION_ES6,
                EvaluatorException.class,
                "redeclaration of variable x",
                "let x; let x;");
    }

    @Test
    public void cannotRedeclareLetWithConst() {
        Utils.assertException(
                Context.VERSION_ES6,
                EvaluatorException.class,
                "redeclaration of variable x",
                "let x; const x = 1;");
    }

    @Test
    public void cannotRedeclareConstWithLet() {
        Utils.assertException(
                Context.VERSION_ES6,
                EvaluatorException.class,
                "redeclaration of const x",
                "const x = 1; let x;");
    }

    @Test
    public void cannotRedeclareVarWithLet() {
        Utils.assertException(
                Context.VERSION_ES6,
                EvaluatorException.class,
                "redeclaration of var x",
                "var x; let x;");
    }

    @Test
    public void cannotRedeclareLetWithVar() {
        Utils.assertException(
                Context.VERSION_ES6,
                EvaluatorException.class,
                "redeclaration of let x",
                "let x; var x;");
    }

    @Test
    public void canDeclareSameNameInNestedBlock() {
        // Should not throw - different scopes
        Utils.assertWithAllModes_ES6(2.0, "let x = 1; { let x = 2; x; }");
    }

    // ===================== Additional Per-Iteration Binding Tests =====================

    @Test
    public void arrowFunctionClosureInLoop() {
        // Arrow functions should also capture per-iteration bindings
        Utils.assertWithAllModes_ES6(
                "[0,1,2]",
                "var funcs = [];\n"
                        + "for (let i = 0; i < 3; i++) {\n"
                        + "    funcs.push(() => i);\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(f => f()));");
    }

    @Test
    public void nestedLoopsWithClosures() {
        // Each loop should have independent per-iteration bindings
        Utils.assertWithAllModes_ES6(
                "[[0,0],[0,1],[1,0],[1,1]]",
                "var funcs = [];\n"
                        + "for (let i = 0; i < 2; i++) {\n"
                        + "    for (let j = 0; j < 2; j++) {\n"
                        + "        funcs.push(() => [i, j]);\n"
                        + "    }\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(f => f()));");
    }

    @Test
    public void closureCapturingOuterAndInnerLoopVar() {
        // Closure captures both outer and inner loop variables
        Utils.assertWithAllModes_ES6(
                "[1,2,3,4]",
                "var funcs = [];\n"
                        + "for (let i = 0; i < 2; i++) {\n"
                        + "    for (let j = 0; j < 2; j++) {\n"
                        + "        funcs.push(() => i * 2 + j + 1);\n"
                        + "    }\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(f => f()));");
    }

    @Test
    public void multipleClosuresPerIteration() {
        // Multiple closures created in same iteration should capture same binding
        Utils.assertWithAllModes_ES6(
                "[[0,0],[1,1],[2,2]]",
                "var funcs1 = [], funcs2 = [];\n"
                        + "for (let i = 0; i < 3; i++) {\n"
                        + "    funcs1.push(() => i);\n"
                        + "    funcs2.push(() => i);\n"
                        + "}\n"
                        + "var result = [];\n"
                        + "for (var k = 0; k < 3; k++) {\n"
                        + "    result.push([funcs1[k](), funcs2[k]()]);\n"
                        + "}\n"
                        + "JSON.stringify(result);");
    }

    @Test
    public void forOfWithArrayValues() {
        // Basic for-of with let and closures
        Utils.assertWithAllModes_ES6(
                "[10,20,30]",
                "var funcs = [];\n"
                        + "for (let x of [10,20,30]) {\n"
                        + "    funcs.push(() => x);\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(f => f()));");
    }

    @Test
    public void iifeInLoop() {
        // IIFE should see per-iteration binding
        Utils.assertWithAllModes_ES6(
                "[0,1,2]",
                "var results = [];\n"
                        + "for (let i = 0; i < 3; i++) {\n"
                        + "    results.push((function() { return i; })());\n"
                        + "}\n"
                        + "JSON.stringify(results);");
    }

    @Test
    public void closureWithLabeledBreak() {
        Utils.assertWithAllModes_ES6(
                "[0,1]",
                "var funcs = [];\n"
                        + "outer: for (let i = 0; i < 5; i++) {\n"
                        + "    funcs.push(() => i);\n"
                        + "    if (i === 1) break outer;\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(f => f()));");
    }

    @Test
    public void closureWithLabeledContinue() {
        Utils.assertWithAllModes_ES6(
                "[0,2,4]",
                "var funcs = [];\n"
                        + "outer: for (let i = 0; i < 5; i++) {\n"
                        + "    if (i % 2 === 1) continue outer;\n"
                        + "    funcs.push(() => i);\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(f => f()));");
    }

    @Test
    public void forLoopWithNoBody() {
        // Empty for loop body should still work
        Utils.assertWithAllModes_ES6(
                3.0, "let count = 0; for (let i = 0; i < 3; i++) count++; count;");
    }

    @Test
    public void forLoopWithBlockBody() {
        Utils.assertWithAllModes_ES6(
                "[0,1,2]",
                "var funcs = [];\n"
                        + "for (let i = 0; i < 3; i++) {\n"
                        + "    {\n"
                        + "        funcs.push(() => i);\n"
                        + "    }\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(f => f()));");
    }

    @Test
    public void forLoopWithMultipleStatements() {
        Utils.assertWithAllModes_ES6(
                "[[0,0],[1,10],[2,20]]",
                "var funcs = [];\n"
                        + "for (let i = 0; i < 3; i++) {\n"
                        + "    let doubled = i * 10;\n"
                        + "    funcs.push(() => [i, doubled]);\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(f => f()));");
    }

    @Test
    public void whileLoopWithLetDoesNotHavePerIterationBindings() {
        // while loops do NOT have per-iteration bindings per spec
        // All closures should capture the same binding
        Utils.assertWithAllModes_ES6(
                "[3,3,3]",
                "var funcs = [];\n"
                        + "let i = 0;\n"
                        + "while (i < 3) {\n"
                        + "    funcs.push(() => i);\n"
                        + "    i++;\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(f => f()));");
    }

    @Test
    public void doWhileLoopWithLetDoesNotHavePerIterationBindings() {
        // do-while loops do NOT have per-iteration bindings per spec
        Utils.assertWithAllModes_ES6(
                "[3,3,3]",
                "var funcs = [];\n"
                        + "let i = 0;\n"
                        + "do {\n"
                        + "    funcs.push(() => i);\n"
                        + "    i++;\n"
                        + "} while (i < 3);\n"
                        + "JSON.stringify(funcs.map(f => f()));");
    }

    // ===================== TDZ Edge Cases =====================

    @Test
    public void tdzTypeofStillThrows() {
        // typeof on TDZ variable should throw in ES6 (unlike regular undefined vars)
        Utils.assertException(
                Context.VERSION_ES6,
                EcmaError.class,
                "ReferenceError: Cannot access 'x' before initialization",
                "{ typeof x; let x = 1; }");
    }

    @Test
    public void tdzInNestedFunction() {
        // TDZ should apply even in nested function bodies
        Utils.assertException(
                Context.VERSION_ES6,
                EcmaError.class,
                "ReferenceError: Cannot access 'x' before initialization",
                "{ (function() { return x; })(); let x = 1; }");
    }

    // ===================== Const Edge Cases =====================

    @Test
    public void constReassignmentInBlockThrows() {
        Utils.assertException(
                Context.VERSION_ES6, EcmaError.class, "TypeError", "{ const x = 1; x = 2; }");
    }

    @Test
    public void constArrayCanBeMutated() {
        Utils.assertWithAllModes_ES6(
                "[1,2,3]",
                "const arr = []; for (let i = 1; i <= 3; i++) arr.push(i); JSON.stringify(arr);");
    }

    // ===================== Block Scoping Edge Cases =====================

    @Test
    public void letInSwitchCase() {
        Utils.assertWithAllModes_ES6(
                2.0, "let x = 1; switch(x) { case 1: { let y = 2; x = y; } } x;");
    }

    @Test
    public void letInTryCatch() {
        Utils.assertWithAllModes_ES6(
                "caught",
                "let result = '';\n"
                        + "try {\n"
                        + "    let x = 1;\n"
                        + "    throw 'error';\n"
                        + "} catch (e) {\n"
                        + "    let x = 'caught';\n"
                        + "    result = x;\n"
                        + "}\n"
                        + "result;");
    }

    @Test
    public void letInTryFinally() {
        Utils.assertWithAllModes_ES6(
                3.0,
                "let result = 0;\n"
                        + "try {\n"
                        + "    let x = 1;\n"
                        + "    result += x;\n"
                        + "} finally {\n"
                        + "    let x = 2;\n"
                        + "    result += x;\n"
                        + "}\n"
                        + "result;");
    }

    @Test
    public void letShadowingInNestedBlocks() {
        Utils.assertWithAllModes_ES6(
                "[1,2,3,2,1]",
                "var results = [];\n"
                        + "let x = 1;\n"
                        + "results.push(x);\n"
                        + "{\n"
                        + "    let x = 2;\n"
                        + "    results.push(x);\n"
                        + "    {\n"
                        + "        let x = 3;\n"
                        + "        results.push(x);\n"
                        + "    }\n"
                        + "    results.push(x);\n"
                        + "}\n"
                        + "results.push(x);\n"
                        + "JSON.stringify(results);");
    }

    // ===================== Edge Cases =====================

    @Test
    public void letWithComplexInitializer() {
        Utils.assertWithAllModes_ES6(3.0, "let x = 1 + 2; x;");
    }

    @Test
    public void multipleLetDeclarations() {
        Utils.assertWithAllModes_ES6(3.0, "let a = 1, b = 2; a + b;");
    }

    @Test
    public void letInArrowFunction() {
        Utils.assertWithAllModes_ES6(1.0, "(() => { let x = 1; return x; })();");
    }

    @Test
    public void letWithComputedInitializer() {
        Utils.assertWithAllModes_ES6(6.0, "let a = 2, b = a * 3; b;");
    }

    @Test
    public void forLoopConditionSeesUpdatedValue() {
        // The condition should see the updated value from increment
        Utils.assertWithAllModes_ES6(
                3.0, "let count = 0; for (let i = 0; i < 3; i++) count++; count;");
    }

    @Test
    public void forLoopIncrementExpression() {
        // Complex increment expression
        Utils.assertWithAllModes_ES6(
                "[0,2,4]",
                "var funcs = [];\n"
                        + "for (let i = 0; i < 6; i += 2) {\n"
                        + "    funcs.push(() => i);\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(f => f()));");
    }

    @Test
    public void forLoopWithDecrementingCounter() {
        Utils.assertWithAllModes_ES6(
                "[2,1,0]",
                "var funcs = [];\n"
                        + "for (let i = 2; i >= 0; i--) {\n"
                        + "    funcs.push(() => i);\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(f => f()));");
    }

    @Test
    public void closureSeesModifiedLoopVariable() {
        // Closure sees the current value of loop variable when called
        // Since we modify i before creating closure and don't restore it,
        // the closure captures the modified value
        Utils.assertWithAllModes_ES6(
                "[10,11,12]",
                "var funcs = [];\n"
                        + "for (let i = 0; i < 3; i++) {\n"
                        + "    let captured = i + 10;\n"
                        + "    funcs.push(() => captured);\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(f => f()));");
    }

    @Test
    public void closureSeesValueAtTimeOfCreation() {
        // When loop variable is modified AFTER closure creation,
        // the closure still references the same binding
        Utils.assertWithAllModes_ES6(
                "[0,1,2]",
                "var funcs = [];\n"
                        + "for (let i = 0; i < 3; i++) {\n"
                        + "    funcs.push(() => i);\n"
                        + "    // This modifies the per-iteration binding\n"
                        + "    // but doesn't affect closure result since we restore it\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(f => f()));");
    }

    @Test
    public void closureCapturesValueAfterModification() {
        // Closure should capture the current value at time of creation
        Utils.assertWithAllModes_ES6(
                "[100,101,102]",
                "var funcs = [];\n"
                        + "for (let i = 0; i < 3; i++) {\n"
                        + "    let captured = i + 100;\n"
                        + "    funcs.push(() => captured);\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(f => f()));");
    }

    @Test
    public void forOfWithString() {
        Utils.assertWithAllModes_ES6(
                "[\"a\",\"b\",\"c\"]",
                "var funcs = [];\n"
                        + "for (let c of 'abc') {\n"
                        + "    funcs.push(() => c);\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(f => f()));");
    }

    @Test
    public void forOfWithGenerator() {
        Utils.assertWithAllModes_ES6(
                "[1,2,3]",
                "function* gen() { yield 1; yield 2; yield 3; }\n"
                        + "var funcs = [];\n"
                        + "for (let x of gen()) {\n"
                        + "    funcs.push(() => x);\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(f => f()));");
    }

    @Test
    public void functionDeclarationInLoop() {
        // Named function declarations in loop should capture per-iteration binding
        Utils.assertWithAllModes_ES6(
                "[0,1,2]",
                "var funcs = [];\n"
                        + "for (let i = 0; i < 3; i++) {\n"
                        + "    function f() { return i; }\n"
                        + "    funcs.push(f);\n"
                        + "}\n"
                        + "JSON.stringify(funcs.map(f => f()));");
    }

    // ===================== Labeled Continue Tests =====================

    @Test
    public void labeledContinueWithNestedWhile() {
        // This mimics test262's labeled-continue.js but with a bounded inner loop
        // The continue label should jump to the outer for loop
        Utils.assertWithAllModes_ES6(
                10.0,
                "var count = 0;\n"
                        + "label: for (let x = 0; x < 10;) {\n"
                        + "    for (var inner = 0; inner < 1; inner++) {\n"
                        + "        x++;\n"
                        + "        count++;\n"
                        + "        continue label;\n"
                        + "    }\n"
                        + "}\n"
                        + "count;");
    }

    @Test
    public void labeledContinueBasic() {
        // Simple labeled continue test
        Utils.assertWithAllModes_ES6(
                3.0,
                "var count = 0;\n"
                        + "outer: for (let i = 0; i < 3; i++) {\n"
                        + "    for (var j = 0; j < 3; j++) {\n"
                        + "        count++;\n"
                        + "        continue outer;\n"
                        + "    }\n"
                        + "}\n"
                        + "count;");
    }

    @Test
    public void labeledContinueWithLetInOuterLoop() {
        // Labeled continue with let in the outer loop
        Utils.assertWithAllModes_ES6(
                "[0,1,2]",
                "var results = [];\n"
                        + "outer: for (let i = 0; i < 3; i++) {\n"
                        + "    for (var j = 0; j < 5; j++) {\n"
                        + "        results.push(i);\n"
                        + "        continue outer;\n"
                        + "    }\n"
                        + "}\n"
                        + "JSON.stringify(results);");
    }

    @Test
    public void labeledContinueWithWhileInsideForLet() {
        // This exactly mimics test262 labeled-continue.js pattern
        // but with a safety guard to prevent infinite loop if broken
        Utils.assertWithAllModes_ES6(
                10.0,
                "var count = 0;\n"
                        + "var safety = 0;\n"
                        + "label: for (let x = 0; x < 10;) {\n"
                        + "    while (safety++ < 100) {\n"
                        + "        x++;\n"
                        + "        count++;\n"
                        + "        continue label;\n"
                        + "    }\n"
                        + "}\n"
                        + "count;");
    }

    @Test
    public void labeledContinueWhileTruePattern() {
        // Test with while(true) pattern and safety counter
        // If continue label works, we exit inner while via continue
        // If broken, safety counter prevents infinite loop
        Utils.assertWithAllModes_ES6(
                5.0,
                "var count = 0;\n"
                        + "var safety = 0;\n"
                        + "label: for (let x = 0; x < 5;) {\n"
                        + "    while (true) {\n"
                        + "        if (safety++ > 100) throw 'safety limit';\n"
                        + "        x++;\n"
                        + "        count++;\n"
                        + "        continue label;\n"
                        + "    }\n"
                        + "}\n"
                        + "count;");
    }

    @Test
    public void labeledContinueWhileTruePatternWithVar() {
        // Same test but with var instead of let - should work
        Utils.assertWithAllModes_ES6(
                5.0,
                "var count = 0;\n"
                        + "var safety = 0;\n"
                        + "label: for (var x = 0; x < 5;) {\n"
                        + "    while (true) {\n"
                        + "        if (safety++ > 100) throw 'safety limit';\n"
                        + "        x++;\n"
                        + "        count++;\n"
                        + "        continue label;\n"
                        + "    }\n"
                        + "}\n"
                        + "count;");
    }

    @Test
    public void labeledContinueNestedForWithLet() {
        // Nested for loops with let - does this work?
        Utils.assertWithAllModes_ES6(
                5.0,
                "var count = 0;\n"
                        + "var safety = 0;\n"
                        + "label: for (let x = 0; x < 5;) {\n"
                        + "    for (;;) {\n"
                        + "        if (safety++ > 100) throw 'safety limit';\n"
                        + "        x++;\n"
                        + "        count++;\n"
                        + "        continue label;\n"
                        + "    }\n"
                        + "}\n"
                        + "count;");
    }

    @Test
    public void simpleNestedForLetLoops() {
        // Simple nested for-let without labeled continue
        // This tests if nested per-iteration scopes work at all
        Utils.assertWithAllModes_ES6(
                6.0,
                "var count = 0;\n"
                        + "for (let x = 0; x < 2; x++) {\n"
                        + "    for (let y = 0; y < 3; y++) {\n"
                        + "        count++;\n"
                        + "    }\n"
                        + "}\n"
                        + "count;");
    }

    @Test
    public void nestedForLetWithIncrementInBody() {
        // This is the pattern from test262 - both loops have increment in body
        Utils.assertWithAllModes_ES6(
                20.0,
                "var count = 0;\n"
                        + "var safety = 0;\n"
                        + "for (let x = 0; x < 10;) {\n"
                        + "    if (safety++ > 200) throw 'outer safety limit';\n"
                        + "    x++;\n"
                        + "    for (let y = 0; y < 2;) {\n"
                        + "        if (safety++ > 200) throw 'inner safety limit';\n"
                        + "        y++;\n"
                        + "        count++;\n"
                        + "    }\n"
                        + "}\n"
                        + "count;");
    }

    // ===================== Generator with For-Let Tests =====================

    @Test
    public void generatorWithForLetAndYieldAfterLoop() {
        // Test for generator bytecode bug: yield after for-let loop
        // Previously failed with IllegalStateException: bad local variable type
        Utils.assertWithAllModes_ES6(
                "[0,1,99]",
                "function* gen() {\n"
                        + "  for (let i = 0; i < 2; i++) {\n"
                        + "    let x = i;\n"
                        + "    yield x;\n"
                        + "  }\n"
                        + "  yield 99;\n"
                        + "}\n"
                        + "var results = [];\n"
                        + "for (var v of gen()) results.push(v);\n"
                        + "JSON.stringify(results);");
    }

    // ===================== Syntax Error Tests (Test262 Compliance) =====================

    @Test
    public void constWithoutInitializerThrowsSyntaxError() {
        // const declarations must have an initializer
        Utils.assertException(
                Context.VERSION_ES6,
                EvaluatorException.class,
                "missing = in const declaration",
                "const x;");
    }

    @Test
    public void constWithoutInitializerInBlockThrowsSyntaxError() {
        Utils.assertException(
                Context.VERSION_ES6,
                EvaluatorException.class,
                "missing = in const declaration",
                "{ const x; }");
    }

    @Test
    public void constMultipleDeclarationsWithoutInitializerThrowsSyntaxError() {
        // const a = 1, b; - b is missing initializer
        Utils.assertException(
                Context.VERSION_ES6,
                EvaluatorException.class,
                "missing = in const declaration",
                "const a = 1, b;");
    }

    @Test
    public void constMixedInitializersThrowsSyntaxError() {
        // const a, b = 1; - a is missing initializer
        Utils.assertException(
                Context.VERSION_ES6,
                EvaluatorException.class,
                "missing = in const declaration",
                "const a, b = 1;");
    }

    @Test
    public void letInIfStatementThrowsSyntaxError() {
        // Lexical declarations are not allowed in statement position
        Utils.assertException(
                Context.VERSION_ES6,
                EvaluatorException.class,
                "lexical declarations",
                "if (true) let x = 1;");
    }

    @Test
    public void letInIfElseStatementThrowsSyntaxError() {
        Utils.assertException(
                Context.VERSION_ES6,
                EvaluatorException.class,
                "lexical declarations",
                "if (true) let x = 1; else let y = 2;");
    }

    @Test
    public void constInIfStatementThrowsSyntaxError() {
        Utils.assertException(
                Context.VERSION_ES6,
                EvaluatorException.class,
                "lexical declarations",
                "if (true) const x = 1;");
    }

    @Test
    public void letInLabeledStatementThrowsSyntaxError() {
        Utils.assertException(
                Context.VERSION_ES6,
                EvaluatorException.class,
                "lexical declarations",
                "label: let x = 1;");
    }

    @Test
    public void constInLabeledStatementThrowsSyntaxError() {
        Utils.assertException(
                Context.VERSION_ES6,
                EvaluatorException.class,
                "lexical declarations",
                "label: const x = 1;");
    }

    @Test
    public void letInWhileBodyThrowsSyntaxError() {
        Utils.assertException(
                Context.VERSION_ES6,
                EvaluatorException.class,
                "let declaration not directly within block",
                "while (false) let x = 1;");
    }

    @Test
    public void letInForBodyThrowsSyntaxError() {
        Utils.assertException(
                Context.VERSION_ES6,
                EvaluatorException.class,
                "let declaration not directly within block",
                "for (;;) let x = 1;");
    }

    @Test
    public void letInBlockIsAllowed() {
        // let inside a block is fine
        Utils.assertWithAllModes_ES6(1.0, "if (true) { let x = 1; x; }");
    }

    @Test
    public void constInBlockIsAllowed() {
        // const inside a block is fine
        Utils.assertWithAllModes_ES6(1.0, "if (true) { const x = 1; x; }");
    }

    @Test
    public void constReassignmentInForLoopNextExpressionThrowsTypeError() {
        // for (const i = 0; i < 1; i++) - i++ should throw TypeError
        Utils.assertException(
                Context.VERSION_ES6,
                EcmaError.class,
                "TypeError",
                "for (const i = 0; i < 1; i++) {}");
    }

    @Test
    public void constInForOfIsAllowed() {
        // for (const x of [1,2,3]) is allowed - new binding each iteration
        Utils.assertWithAllModes_ES6(6.0, "var sum = 0; for (const x of [1,2,3]) sum += x; sum;");
    }

    @Test
    public void constInForInIsAllowed() {
        // for (const k in {a:1}) is allowed - new binding each iteration
        Utils.assertWithAllModes_ES6("a", "var result; for (const k in {a:1}) result = k; result;");
    }

    @Test
    public void constOuterInnerBindings() {
        // Outer const should be unchanged by inner const in for loop
        Utils.assertWithAllModes_ES6(
                "outer_x",
                "const x = 'outer_x';\n"
                        + "for (var i = 0; i < 1; i++) {\n"
                        + "    const x = 'inner_x';\n"
                        + "}\n"
                        + "x;");
    }

    @Test
    public void letClosureInsideForCondition() {
        // Closure created in for loop condition should capture per-iteration binding
        Utils.assertWithAllModes_ES6(
                "[0,1,2,3,4]",
                "var a = [];\n"
                        + "for (let i = 0; a.push(function() { return i; }), i < 5; ++i) {}\n"
                        + "JSON.stringify(a.map(function(f) { return f(); }));");
    }

    @Test
    public void letClosureInsideForInitialization() {
        // Closure created in for loop initialization
        Utils.assertWithAllModes_ES6(
                0.0,
                "var f;\n"
                        + "for (let i = 0, g = function() { return i; }; i < 1; i++) { f = g; }\n"
                        + "f();");
    }

    @Test
    public void letClosureInsideForNextExpression() {
        // Closure created in for loop next expression
        Utils.assertWithAllModes_ES6(
                "[1,2,3,4,5]",
                "var a = [];\n"
                        + "for (let i = 0; i < 5; a.push(function() { return i; }), ++i) {}\n"
                        + "JSON.stringify(a.map(function(f) { return f(); }));");
    }
}
