/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.node.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal standalone JSON parser for package.json files. Returns {@link Map} (LinkedHashMap for
 * insertion-order preservation), {@link List}, {@link String}, {@link Number}, {@link Boolean}, or
 * {@code null}. Does not depend on Rhino Context or Scope.
 */
public class SimpleJsonParser {

    private final String input;
    private int pos;

    private SimpleJsonParser(String input) {
        this.input = input;
        this.pos = 0;
    }

    public static Object parse(String json) {
        SimpleJsonParser parser = new SimpleJsonParser(json);
        Object result = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos != parser.input.length()) {
            throw parser.error("Unexpected trailing content");
        }
        return result;
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= input.length()) {
            throw error("Unexpected end of input");
        }
        char c = input.charAt(pos);
        switch (c) {
            case '"':
                return parseString();
            case '{':
                return parseObject();
            case '[':
                return parseArray();
            case 't':
            case 'f':
                return parseBoolean();
            case 'n':
                return parseNull();
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return parseNumber();
                }
                throw error("Unexpected character: " + c);
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= input.length()) {
                    throw error("Unexpected end of string");
                }
                char esc = input.charAt(pos++);
                switch (esc) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        sb.append(parseUnicodeEscape());
                        break;
                    default:
                        throw error("Invalid escape: \\" + esc);
                }
            } else {
                sb.append(c);
            }
        }
        throw error("Unterminated string");
    }

    private char parseUnicodeEscape() {
        if (pos + 4 > input.length()) {
            throw error("Invalid unicode escape");
        }
        String hex = input.substring(pos, pos + 4);
        pos += 4;
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw error("Invalid unicode escape: \\u" + hex);
        }
    }

    private Map<String, Object> parseObject() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            if (pos >= input.length()) {
                throw error("Unterminated object");
            }
            char c = input.charAt(pos);
            if (c == '}') {
                pos++;
                return map;
            }
            if (c != ',') {
                throw error("Expected ',' or '}' in object");
            }
            pos++;
        }
    }

    private List<Object> parseArray() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(parseValue());
            skipWhitespace();
            if (pos >= input.length()) {
                throw error("Unterminated array");
            }
            char c = input.charAt(pos);
            if (c == ']') {
                pos++;
                return list;
            }
            if (c != ',') {
                throw error("Expected ',' or ']' in array");
            }
            pos++;
        }
    }

    private Number parseNumber() {
        int start = pos;
        if (pos < input.length() && input.charAt(pos) == '-') {
            pos++;
        }
        if (pos >= input.length()) {
            throw error("Unexpected end of number");
        }
        if (input.charAt(pos) == '0') {
            pos++;
        } else if (input.charAt(pos) >= '1' && input.charAt(pos) <= '9') {
            pos++;
            while (pos < input.length() && input.charAt(pos) >= '0' && input.charAt(pos) <= '9') {
                pos++;
            }
        } else {
            throw error("Invalid number");
        }
        boolean isFloat = false;
        if (pos < input.length() && input.charAt(pos) == '.') {
            isFloat = true;
            pos++;
            if (pos >= input.length() || input.charAt(pos) < '0' || input.charAt(pos) > '9') {
                throw error("Invalid number: expected digit after decimal point");
            }
            while (pos < input.length() && input.charAt(pos) >= '0' && input.charAt(pos) <= '9') {
                pos++;
            }
        }
        if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
            isFloat = true;
            pos++;
            if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                pos++;
            }
            if (pos >= input.length() || input.charAt(pos) < '0' || input.charAt(pos) > '9') {
                throw error("Invalid number: expected digit in exponent");
            }
            while (pos < input.length() && input.charAt(pos) >= '0' && input.charAt(pos) <= '9') {
                pos++;
            }
        }
        String num = input.substring(start, pos);
        if (isFloat) {
            return Double.parseDouble(num);
        }
        long l = Long.parseLong(num);
        if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
            return (int) l;
        }
        return l;
    }

    private Boolean parseBoolean() {
        if (input.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (input.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw error("Expected 'true' or 'false'");
    }

    private Object parseNull() {
        if (input.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw error("Expected 'null'");
    }

    private void skipWhitespace() {
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                pos++;
            } else {
                break;
            }
        }
    }

    private void expect(char expected) {
        if (pos >= input.length() || input.charAt(pos) != expected) {
            throw error("Expected '" + expected + "'");
        }
        pos++;
    }

    private RuntimeException error(String message) {
        return new IllegalArgumentException(message + " at position " + pos);
    }
}
