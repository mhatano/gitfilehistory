/*
 * Copyright (c) 2026 Manami Hatano
 *
 * Licensed under the MIT License. See LICENSE.md in project root for details.
 */
package jp.hatano.gitfilehistory;

import org.junit.Test;
import org.junit.Assume;
import static org.junit.Assert.*;
import javax.swing.*;
import java.awt.*;

public class GitDiffViewerTest {

    @Test
    public void testEscapeHtml() {
        assertEquals("a&amp;b", GitDiffViewer.escapeHtml("a&b"));
        assertEquals("&lt;tag&gt;", GitDiffViewer.escapeHtml("<tag>"));
        assertEquals("&quot;quote&quot;", GitDiffViewer.escapeHtml("\"quote\""));
        assertEquals("&#39;single&#39;", GitDiffViewer.escapeHtml("'single'"));
        assertEquals("normal text", GitDiffViewer.escapeHtml("normal text"));
        assertEquals("", GitDiffViewer.escapeHtml(null));
        assertEquals("", GitDiffViewer.escapeHtml(""));
    }

    @Test
    public void testSortCommitsWithUncommitted() {
        CommitInfo c1 = new CommitInfo("Uncommitted 1", "Author", "2026-01-01 00:00:00");
        CommitInfo c2 = new CommitInfo("Uncommitted 2", "Author", "2026-01-01 01:00:00");
        
        // Since both have getCommit() == null, both get Long.MAX_VALUE.
        // The implementation uses <=, so c1 should be index 0.
        CommitInfo[] sorted = GitDiffViewer.sortCommits(c1, c2);
        assertEquals(c1, sorted[0]);
        assertEquals(c2, sorted[1]);

        CommitInfo[] sortedReverse = GitDiffViewer.sortCommits(c2, c1);
        assertEquals(c2, sortedReverse[0]);
        assertEquals(c1, sortedReverse[1]);
    }

    @Test
    public void testInitialization() {
        // Skip this test if running in a headless environment (like some CI servers)
        Assume.assumeFalse("Skipping UI test in headless environment", GraphicsEnvironment.isHeadless());
        
        GitDiffViewer viewer = new GitDiffViewer();
        assertNotNull(viewer);
        assertEquals("Git File Diff Viewer", viewer.getTitle());
        assertEquals(JFrame.EXIT_ON_CLOSE, viewer.getDefaultCloseOperation());
    }

    @Test
    public void testBasicComponentStructure() {
        Assume.assumeFalse("Skipping UI test in headless environment", GraphicsEnvironment.isHeadless());
        
        GitDiffViewer viewer = new GitDiffViewer();
        Container contentPane = viewer.getContentPane();
        assertNotNull("Content pane should not be null", contentPane);
        assertTrue(contentPane.getLayout() instanceof BorderLayout);
        
        BorderLayout layout = (BorderLayout) contentPane.getLayout();
        assertNotNull("North component should be present", layout.getLayoutComponent(BorderLayout.NORTH));
        assertNotNull("Center component should be present", layout.getLayoutComponent(BorderLayout.CENTER));
        assertNotNull("South component should be present", layout.getLayoutComponent(BorderLayout.SOUTH));
    }
}
