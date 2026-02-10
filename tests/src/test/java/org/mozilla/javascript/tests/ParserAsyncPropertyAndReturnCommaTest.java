/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests;

import org.junit.Test;
import org.mozilla.javascript.testutils.Utils;

public class ParserAsyncPropertyAndReturnCommaTest {

    @Test
    public void asyncAsPropertyName() {
        Utils.assertWithAllModes_ES6(true, Utils.lines("var o = {async: true};", "o.async"));
    }

    @Test
    public void asyncAsPropertyNameWithOtherProperties() {
        Utils.assertWithAllModes_ES6(2, Utils.lines("var o = {async: 1, b: 2};", "o.b"));
    }

    @Test
    public void asyncAsShorthandProperty() {
        Utils.assertWithAllModes_ES6(
                42, Utils.lines("var async = 42;", "var o = {async};", "o.async"));
    }

    @Test
    public void asyncMethodInObjectLiteral() {
        Utils.assertWithAllModes_ES6(
                "function", Utils.lines("var o = { async foo() { return 1; } };", "typeof o.foo"));
    }

    @Test
    public void asyncPropertyWithMethodAfter() {
        Utils.assertWithAllModes_ES6(
                3, Utils.lines("var o = { async: 1, foo() { return 3; } };", "o.foo()"));
    }

    @Test
    public void returnWithCommaOperator() {
        Utils.assertWithAllModes_ES6(2, Utils.lines("function f() { return 1, 2; }", "f()"));
    }

    @Test
    public void returnWithCommaOperatorParenthesized() {
        Utils.assertWithAllModes_ES6(3, Utils.lines("function f() { return (1+2), 3; }", "f()"));
    }

    @Test
    public void returnWithMultipleCommaOperators() {
        Utils.assertWithAllModes_ES6(3, Utils.lines("function f() { return 1, 2, 3; }", "f()"));
    }

    @Test
    public void returnWithCommaOperatorSideEffects() {
        Utils.assertWithAllModes_ES6(
                "1-2",
                Utils.lines("var x = 0;", "function f() { return x = 1, x + '-' + 2; }", "f()"));
    }
}
