package org.mozilla.javascript.tests;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.mozilla.javascript.Context;

public class SimpleDestructuringTest {

    public Object eval(String source) {
        try (final Context cx = Context.enter()) {
            cx.setOptimizationLevel(-1);
            cx.setLanguageVersion(Context.VERSION_ES6);
            return cx.evaluateString(cx.initStandardObjects(), source, "test", 1, null);
        }
    }

    @Test
    public void simpleDestructuring() {
        try (final Context cx = Context.enter()) {
            cx.setOptimizationLevel(-1);
            cx.setLanguageVersion(Context.VERSION_ES6);
            eval("let x = 223; let y = 3; let z = y[1];");
            assertEquals(2.0, eval("let [x] = [2]; x"));
            assertEquals(5.0, eval("let [x, y] = [2, 3]; x + y"));
//            assertEquals(5.0, eval("let x,y; let $0 = [2, 3]; x = $0[0]; y = $0[1]; x + y"));
//            assertEquals(5.0, eval("let x,y; [x, y] = [2, 3]; x + y"));
//
//            assertEquals(5.0, eval("var x,y; x = 2; y = 3; x + y"));
//            assertEquals(5.0, eval("var x,y; [x, y] = [2, 3]; x + y"));
            assertEquals(5.0, eval("let [x, [y]] = [2, [3]]; x + y"));
            // I assume this will break because I have only been paying attention to the array path
            assertEquals(5.0, eval("let {x, y} = {x: 2, y: 3}; x + y"));
            // Super complicated example
            assertEquals(5.0, eval("let {x, y: [z]} = {x: 2, y: [3]}; x + z"));
            assertEquals(5.0, eval("(function({ x, y }) { return x + y; })({ x: 2, y: 3 })"));
            assertEquals(
                    5.0, eval("(function({ x, y: [z] }) { return x + z; })({ x: 2, y: [3] })"));
            assertEquals(5.0, eval("(function f(x = 2) { return x + 3; })()"));
            assertEquals(5.0, eval("(function f([x] = [2]) {\n return x + 3;\n })()"));
            assertEquals(5.0, eval("(function f([x = 2] = [3]) {\n return x + 3;\n })([])"));
            assertEquals(5.0, eval("(function f([x = 3] = [2]) {\n return x + 3;\n })()"));
//            assertEquals(5.0, eval("var x,y; [x,y] = [2,3]; x + y"));
        }
    }
}

