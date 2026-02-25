/*
 * Copyright (c) 2026 jp.hatano.gitfilehistory
 *
 * Licensed under the MIT License. See LICENSE.md in project root for details.
 */
package jp.hatano.gitfilehistory;

import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple diff utility class to find differences between two lists of strings.
 * Uses a basic Longest Common Subsequence (LCS) approach.
 */
public class DiffUtils {
    public enum DiffType { EQUAL, DELETE, INSERT, CHANGE }

    public static class Diff {
        public final DiffType type;
        public final List<String> lines;
        public final List<String> oldLines; // For CHANGE type
        public final List<String> newLines; // For CHANGE type

        Diff(DiffType type, List<String> lines) {
            this.type = type;
            this.lines = lines;
            this.oldLines = null;
            this.newLines = null;
        }
        Diff(DiffType type, List<String> newLines, List<String> oldLines) {
            this.type = type;
            this.lines = newLines; // For INSERT, this is the main content
            this.oldLines = oldLines;
            this.newLines = newLines;
        }
    }

    public static List<Diff> diff(List<String> oldLines, List<String> newLines) {
        final List<Diff> diffs = new ArrayList<>();
        final Patch<String> patch = com.github.difflib.DiffUtils.diff(oldLines, newLines);

        int lastOldPos = 0;
        for (final AbstractDelta<String> delta : patch.getDeltas()) {
            final int currentOldPos = delta.getSource().getPosition();

            // Add EQUAL block for lines between the last delta and this one
            if (lastOldPos < currentOldPos) {
                diffs.add(new Diff(DiffType.EQUAL, oldLines.subList(lastOldPos, currentOldPos)));
            }

            // Handle the delta itself
            if (delta.getType() == DeltaType.CHANGE) {
                diffs.add(new Diff(DiffType.CHANGE, delta.getTarget().getLines(), delta.getSource().getLines()));
            } else if (delta.getType() == DeltaType.DELETE) {
                diffs.add(new Diff(DiffType.DELETE, delta.getSource().getLines()));
            } else if (delta.getType() == DeltaType.INSERT) {
                diffs.add(new Diff(DiffType.INSERT, delta.getTarget().getLines()));
            }
            
            // Update the position for the next EQUAL block
            lastOldPos = delta.getSource().getPosition() + delta.getSource().size();
        }

        // Add the final EQUAL block if there are any lines left at the end
        if (lastOldPos < oldLines.size()) {
            diffs.add(new Diff(DiffType.EQUAL, oldLines.subList(lastOldPos, oldLines.size())));
        }

        return diffs;
    }
}