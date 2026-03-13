/*
 * Copyright (c) 2026 Manami Hatano
 *
 * Licensed under the MIT License. See LICENSE.md in project root for details.
 */
package jp.hatano.gitfilehistory;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Manages search and highlighting functionality for the diff panes.
 */
public class SearchManager {
    private final JTextPane leftPane;
    private final JTextPane rightPane;
    private final JLabel statusBar;

    private final Highlighter.HighlightPainter searchHighlightPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 255, 0, 128));
    private final Highlighter.HighlightPainter currentHighlightPainter = new DefaultHighlighter.DefaultHighlightPainter(Color.ORANGE);
    
    private final List<HighlightInfo> highlights = new ArrayList<>();
    private int currentHighlightIndex = -1;

    private static class HighlightInfo {
        final JTextPane pane;
        final int start;
        final int end;
        Object tag;

        HighlightInfo(JTextPane pane, int start, int end) {
            this.pane = pane;
            this.start = start;
            this.end = end;
        }
    }

    public SearchManager(JTextPane leftPane, JTextPane rightPane, JLabel statusBar) {
        this.leftPane = leftPane;
        this.rightPane = rightPane;
        this.statusBar = statusBar;
    }

    public void clearHighlights() {
        if (leftPane != null) leftPane.getHighlighter().removeAllHighlights();
        if (rightPane != null) rightPane.getHighlighter().removeAllHighlights();
        highlights.clear();
        currentHighlightIndex = -1;
    }

    public void updateHighlights(String searchText, boolean ignoreCase, boolean isRegex) {
        clearHighlights();

        if (searchText == null || searchText.isEmpty()) {
            statusBar.setText("Ready");
            return;
        }

        try {
            addHighlightsInPane(leftPane, searchText, ignoreCase, isRegex);
            addHighlightsInPane(rightPane, searchText, ignoreCase, isRegex);
        } catch (PatternSyntaxException e) {
            statusBar.setText("Invalid Regex: " + e.getMessage());
            return;
        }

        if (!highlights.isEmpty()) {
            currentHighlightIndex = 0;
            navigateToCurrentHighlight(false);
        } else {
            statusBar.setText("Text not found: " + searchText);
        }
    }

    private void addHighlightsInPane(JTextPane pane, String searchText, boolean ignoreCase, boolean isRegex) throws PatternSyntaxException {
        try {
            Document doc = pane.getDocument();
            String content = doc.getText(0, doc.getLength());

            Pattern pattern;
            int flags = ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
            if (isRegex) {
                pattern = Pattern.compile(searchText, flags);
            } else {
                pattern = Pattern.compile(Pattern.quote(searchText), flags);
            }

            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                HighlightInfo hi = new HighlightInfo(pane, matcher.start(), matcher.end());
                hi.tag = pane.getHighlighter().addHighlight(hi.start, hi.end, searchHighlightPainter);
                highlights.add(hi);
            }
        } catch (BadLocationException e) {
            // Should not happen for valid ranges
        }
    }

    public void navigateHighlights(boolean forward) {
        if (highlights.isEmpty()) return;

        if (currentHighlightIndex != -1) {
            HighlightInfo oldHi = highlights.get(currentHighlightIndex);
            oldHi.pane.getHighlighter().removeHighlight(oldHi.tag);
            try {
                oldHi.tag = oldHi.pane.getHighlighter().addHighlight(oldHi.start, oldHi.end, searchHighlightPainter);
            } catch (BadLocationException e) { /* ignore */ }
        }

        if (forward) {
            currentHighlightIndex = (currentHighlightIndex + 1) % highlights.size();
        } else {
            currentHighlightIndex = (currentHighlightIndex - 1 + highlights.size()) % highlights.size();
        }

        navigateToCurrentHighlight(true);
    }

    private void navigateToCurrentHighlight(boolean scroll) {
        if (currentHighlightIndex < 0 || currentHighlightIndex >= highlights.size()) return;

        HighlightInfo hi = highlights.get(currentHighlightIndex);
        hi.pane.getHighlighter().removeHighlight(hi.tag);
        try {
            hi.tag = hi.pane.getHighlighter().addHighlight(hi.start, hi.end, currentHighlightPainter);
        } catch (BadLocationException e) { /* ignore */ }

        if (scroll) {
            try {
                Rectangle2D viewRect2D = hi.pane.modelToView2D(hi.start);
                if (viewRect2D != null) {
                    hi.pane.scrollRectToVisible(viewRect2D.getBounds());
                }
                hi.pane.setCaretPosition(hi.start);
            } catch (BadLocationException e) { /* ignore */ }
        }

        statusBar.setText("Match " + (currentHighlightIndex + 1) + " of " + highlights.size());
    }
}
