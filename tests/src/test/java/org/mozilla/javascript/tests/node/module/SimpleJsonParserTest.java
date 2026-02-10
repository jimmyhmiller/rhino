/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests.node.module;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.node.module.SimpleJsonParser;

class SimpleJsonParserTest {

    @Test
    void parsesEmptyObject() {
        Object result = SimpleJsonParser.parse("{}");
        assertInstanceOf(Map.class, result);
        assertTrue(((Map<?, ?>) result).isEmpty());
    }

    @Test
    void parsesEmptyArray() {
        Object result = SimpleJsonParser.parse("[]");
        assertInstanceOf(List.class, result);
        assertTrue(((List<?>) result).isEmpty());
    }

    @Test
    void parsesString() {
        assertEquals("hello", SimpleJsonParser.parse("\"hello\""));
    }

    @Test
    void parsesStringWithEscapes() {
        assertEquals("a\"b\\c", SimpleJsonParser.parse("\"a\\\"b\\\\c\""));
        assertEquals("line\nbreak", SimpleJsonParser.parse("\"line\\nbreak\""));
        assertEquals("\t\r\b\f", SimpleJsonParser.parse("\"\\t\\r\\b\\f\""));
    }

    @Test
    void parsesUnicodeEscapes() {
        assertEquals("\u0041", SimpleJsonParser.parse("\"\\u0041\""));
    }

    @Test
    void parsesIntegers() {
        assertEquals(42, SimpleJsonParser.parse("42"));
        assertEquals(0, SimpleJsonParser.parse("0"));
        assertEquals(-7, SimpleJsonParser.parse("-7"));
    }

    @Test
    void parsesFloats() {
        assertEquals(3.14, SimpleJsonParser.parse("3.14"));
        assertEquals(-0.5, SimpleJsonParser.parse("-0.5"));
        assertEquals(1e10, SimpleJsonParser.parse("1e10"));
        assertEquals(2.5E-3, SimpleJsonParser.parse("2.5E-3"));
    }

    @Test
    void parsesBooleans() {
        assertEquals(true, SimpleJsonParser.parse("true"));
        assertEquals(false, SimpleJsonParser.parse("false"));
    }

    @Test
    void parsesNull() {
        assertNull(SimpleJsonParser.parse("null"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void parsesPackageJson() {
        String json =
                "{\n"
                        + "  \"name\": \"my-package\",\n"
                        + "  \"version\": \"1.0.0\",\n"
                        + "  \"main\": \"./lib/index.js\",\n"
                        + "  \"type\": \"module\",\n"
                        + "  \"exports\": {\n"
                        + "    \".\": {\n"
                        + "      \"import\": \"./esm/index.mjs\",\n"
                        + "      \"require\": \"./lib/index.js\"\n"
                        + "    },\n"
                        + "    \"./utils\": \"./lib/utils.js\"\n"
                        + "  }\n"
                        + "}";
        Map<String, Object> result = (Map<String, Object>) SimpleJsonParser.parse(json);
        assertEquals("my-package", result.get("name"));
        assertEquals("1.0.0", result.get("version"));
        assertEquals("./lib/index.js", result.get("main"));
        assertEquals("module", result.get("type"));

        Map<String, Object> exports = (Map<String, Object>) result.get("exports");
        assertNotNull(exports);
        assertEquals("./lib/utils.js", exports.get("./utils"));

        Map<String, Object> dotExport = (Map<String, Object>) exports.get(".");
        assertEquals("./esm/index.mjs", dotExport.get("import"));
        assertEquals("./lib/index.js", dotExport.get("require"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void preservesInsertionOrder() {
        String json = "{\"z\": 1, \"a\": 2, \"m\": 3}";
        Map<String, Object> result = (Map<String, Object>) SimpleJsonParser.parse(json);
        List<String> keys = List.copyOf(result.keySet());
        assertEquals(List.of("z", "a", "m"), keys);
    }

    @Test
    void parsesNestedArrays() {
        Object result = SimpleJsonParser.parse("[1, [2, 3], [4, [5]]]");
        assertInstanceOf(List.class, result);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) result;
        assertEquals(3, list.size());
        assertEquals(1, list.get(0));
        @SuppressWarnings("unchecked")
        List<Object> inner = (List<Object>) list.get(1);
        assertEquals(2, inner.get(0));
    }

    @Test
    void handlesWhitespace() {
        Object result = SimpleJsonParser.parse("  {  \"a\"  :  1  }  ");
        assertInstanceOf(Map.class, result);
    }

    @Test
    void rejectsTrailingContent() {
        assertThrows(IllegalArgumentException.class, () -> SimpleJsonParser.parse("{} extra"));
    }

    @Test
    void rejectsUnterminatedString() {
        assertThrows(
                IllegalArgumentException.class, () -> SimpleJsonParser.parse("\"unterminated"));
    }

    @Test
    void rejectsInvalidEscape() {
        assertThrows(IllegalArgumentException.class, () -> SimpleJsonParser.parse("\"\\x\""));
    }
}
