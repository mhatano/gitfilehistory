/*
 * Copyright (c) 2026 Manami Hatano
 *
 * Licensed under the MIT License. See LICENSE.md in project root for details.
 */
package jp.hatano.gitfilehistory;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class DiffUtilsTest {

    @Test
    public void testDiffEmptyLists() {
        List<String> oldLines = Collections.emptyList();
        List<String> newLines = Collections.emptyList();

        List<DiffUtils.Diff> diffs = DiffUtils.diff(oldLines, newLines);

        assertTrue(diffs.isEmpty());
    }

    @Test
    public void testDiffIdenticalLists() {
        List<String> oldLines = Arrays.asList("line1", "line2", "line3");
        List<String> newLines = Arrays.asList("line1", "line2", "line3");

        List<DiffUtils.Diff> diffs = DiffUtils.diff(oldLines, newLines);

        assertEquals(1, diffs.size());
        assertEquals(DiffUtils.DiffType.EQUAL, diffs.get(0).type);
        assertEquals(oldLines, diffs.get(0).lines);
    }

    @Test
    public void testDiffWithInsert() {
        List<String> oldLines = Arrays.asList("line1", "line2");
        List<String> newLines = Arrays.asList("line1", "inserted", "line2");

        List<DiffUtils.Diff> diffs = DiffUtils.diff(oldLines, newLines);

        assertEquals(3, diffs.size());
        assertEquals(DiffUtils.DiffType.EQUAL, diffs.get(0).type);
        assertEquals(Arrays.asList("line1"), diffs.get(0).lines);
        assertEquals(DiffUtils.DiffType.INSERT, diffs.get(1).type);
        assertEquals(Arrays.asList("inserted"), diffs.get(1).lines);
        assertEquals(DiffUtils.DiffType.EQUAL, diffs.get(2).type);
        assertEquals(Arrays.asList("line2"), diffs.get(2).lines);
    }

    @Test
    public void testDiffWithDelete() {
        List<String> oldLines = Arrays.asList("line1", "deleted", "line2");
        List<String> newLines = Arrays.asList("line1", "line2");

        List<DiffUtils.Diff> diffs = DiffUtils.diff(oldLines, newLines);

        assertEquals(3, diffs.size());
        assertEquals(DiffUtils.DiffType.EQUAL, diffs.get(0).type);
        assertEquals(Arrays.asList("line1"), diffs.get(0).lines);
        assertEquals(DiffUtils.DiffType.DELETE, diffs.get(1).type);
        assertEquals(Arrays.asList("deleted"), diffs.get(1).lines);
        assertEquals(DiffUtils.DiffType.EQUAL, diffs.get(2).type);
        assertEquals(Arrays.asList("line2"), diffs.get(2).lines);
    }

    @Test
    public void testDiffWithChange() {
        List<String> oldLines = Arrays.asList("line1", "old", "line3");
        List<String> newLines = Arrays.asList("line1", "new", "line3");

        List<DiffUtils.Diff> diffs = DiffUtils.diff(oldLines, newLines);

        assertEquals(3, diffs.size());
        assertEquals(DiffUtils.DiffType.EQUAL, diffs.get(0).type);
        assertEquals(Arrays.asList("line1"), diffs.get(0).lines);
        assertEquals(DiffUtils.DiffType.CHANGE, diffs.get(1).type);
        assertEquals(Arrays.asList("new"), diffs.get(1).lines);
        assertEquals(Arrays.asList("old"), diffs.get(1).oldLines);
        assertEquals(DiffUtils.DiffType.EQUAL, diffs.get(2).type);
        assertEquals(Arrays.asList("line3"), diffs.get(2).lines);
    }
}