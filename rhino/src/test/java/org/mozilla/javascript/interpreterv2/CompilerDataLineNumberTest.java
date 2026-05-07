package org.mozilla.javascript.interpreterv2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.mozilla.javascript.Context;

public class CompilerDataLineNumberTest {

    private CompilerData<?> build(LineNumberTable.Builder lineNumberTable) {
        CompilerData.Builder<?> b =
                new CompilerData.Builder<>(
                        Context.VERSION_ES6, "test", true, CompilerData.FunctionType.Script);
        b.setLineNumberTable(lineNumberTable);
        return b.build();
    }

    @Test
    public void emptyLineNumberTable() {
        LineNumberTable.Builder lineNumberTable = new LineNumberTable.Builder();

        CompilerData<?> cData = build(lineNumberTable);
        assertArrayEquals(new int[0], cData.getLineNumbers());
        assertEquals(-1, cData.getFirstLineNumber());
        assertEquals(-1, cData.getPcFirstLineNumber());
        assertEquals("[]", cData.getLineNumberTableForDebug());
        assertEquals(-1, cData.getLineNumberFromPc(0));
        assertEquals(-1, cData.getLineNumberFromPc(99));
    }

    @Test
    public void lineNumberTableWithOneEntry() {
        LineNumberTable.Builder lineNumberTable = new LineNumberTable.Builder();
        lineNumberTable.add(1, 42, 42);

        CompilerData<?> cData = build(lineNumberTable);
        assertArrayEquals(new int[] {42}, cData.getLineNumbers());
        assertEquals(42, cData.getFirstLineNumber());
        assertEquals(1, cData.getPcFirstLineNumber());
        assertEquals("[1 -> [42]]", cData.getLineNumberTableForDebug());
        assertEquals(-1, cData.getLineNumberFromPc(0));
        assertEquals(42, cData.getLineNumberFromPc(99));
    }

    @Test
    public void lineNumberTableWithTwoEntries() {
        LineNumberTable.Builder lineNumberTable = new LineNumberTable.Builder();
        lineNumberTable.add(1, 1, 1);
        lineNumberTable.add(7, 2, 2);

        CompilerData<?> cData = build(lineNumberTable);
        assertArrayEquals(new int[] {1, 2}, cData.getLineNumbers());
        assertEquals(1, cData.getFirstLineNumber());
        assertEquals(1, cData.getPcFirstLineNumber());
        assertEquals("[1 -> [1],  7 -> [2]]", cData.getLineNumberTableForDebug());
        assertEquals(-1, cData.getLineNumberFromPc(0));
        assertEquals(1, cData.getLineNumberFromPc(1));
        assertEquals(1, cData.getLineNumberFromPc(6));
        assertEquals(2, cData.getLineNumberFromPc(7));
        assertEquals(2, cData.getLineNumberFromPc(99));
    }

    @Test
    public void lineNumberTableLarge() {
        LineNumberTable.Builder lineNumberTable = new LineNumberTable.Builder();
        lineNumberTable.add(1, 2, 2);
        lineNumberTable.add(6, 3, 4);
        lineNumberTable.add(8, 5, 6);

        CompilerData<?> cData = build(lineNumberTable);
        assertArrayEquals(new int[] {2, 3, 4, 5, 6}, cData.getLineNumbers());
        assertEquals(2, cData.getFirstLineNumber());
        assertEquals(1, cData.getPcFirstLineNumber());
        assertEquals("[1 -> [2],  6 -> [3, 4],  8 -> [5, 6]]", cData.getLineNumberTableForDebug());
        assertEquals(-1, cData.getLineNumberFromPc(0));
        assertEquals(2, cData.getLineNumberFromPc(1));
        assertEquals(4, cData.getLineNumberFromPc(6));
        assertEquals(4, cData.getLineNumberFromPc(7));
        assertEquals(6, cData.getLineNumberFromPc(8));
        assertEquals(6, cData.getLineNumberFromPc(99));
    }
}
