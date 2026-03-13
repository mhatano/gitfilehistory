/*
 * Copyright (c) 2026 Manami Hatano
 *
 * Licensed under the MIT License. See LICENSE.md in project root for details.
 */
package jp.hatano.gitfilehistory;

import org.junit.Test;
import static org.junit.Assert.*;

public class CommitInfoTest {

    @Test
    public void testConstructorWithStrings() {
        CommitInfo commitInfo = new CommitInfo("WIP message", "WIP Author", "2023-01-01 12:00:00");

        assertEquals("[WIP]", commitInfo.getShortHash());
        assertEquals("WIP Author", commitInfo.author);
        assertEquals("2023-01-01 12:00:00", commitInfo.date);
        assertEquals("WIP message", commitInfo.message);
        assertTrue(commitInfo.isUncommitted());
        assertNull(commitInfo.getCommit());
        assertTrue(commitInfo.branchNames.isEmpty());
    }

    @Test
    public void testToString() {
        CommitInfo commitInfo = new CommitInfo("Test message", "Test Author", "2023-01-01 12:00:00");
        String expected = "[WIP] - Test message (Test Author)";
        assertEquals(expected, commitInfo.toString());
    }

    @Test
    public void testEqualsAndHashCode() {
        CommitInfo info1 = new CommitInfo("msg", "author", "date");
        CommitInfo info2 = new CommitInfo("msg", "author", "date");
        CommitInfo info3 = new CommitInfo("different", "author", "date");

        assertEquals(info1, info2);
        assertNotEquals(info1, info3);
        assertEquals(info1.hashCode(), info2.hashCode());
        assertNotEquals(info1.hashCode(), info3.hashCode());
    }
}