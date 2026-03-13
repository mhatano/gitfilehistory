/*
 * Copyright (c) 2026 Manami Hatano
 *
 * Licensed under the MIT License. See LICENSE.md in project root for details.
 */
package jp.hatano.gitfilehistory;

import org.junit.Test;

import javax.swing.JTextPane;
import java.util.List;

import static org.junit.Assert.*;

public class LineWrapperTest {

    @Test
    public void testWrapEmptyText() {
        JTextPane textPane = new JTextPane();
        textPane.setSize(100, 100); // Set some size
        LineWrapper wrapper = new LineWrapper(textPane);

        List<String> result = wrapper.wrap("");

        assertEquals(1, result.size());
        assertEquals("", result.get(0));
    }

    @Test
    public void testWrapShortText() {
        JTextPane textPane = new JTextPane();
        textPane.setSize(1000, 100); // Wide enough
        LineWrapper wrapper = new LineWrapper(textPane);

        List<String> result = wrapper.wrap("Hello world");

        assertEquals(1, result.size());
        assertEquals("Hello world", result.get(0));
    }

    @Test
    public void testWrapWithIndent() {
        JTextPane textPane = new JTextPane();
        textPane.setSize(1000, 100);
        LineWrapper wrapper = new LineWrapper(textPane);

        List<String> result = wrapper.wrap("    Indented text");

        assertEquals(1, result.size());
        assertEquals("    Indented text", result.get(0));
    }
}