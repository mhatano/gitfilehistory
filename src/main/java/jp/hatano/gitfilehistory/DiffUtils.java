package jp.hatano.gitfilehistory;

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
        List<Diff> diffs = new ArrayList<>();
        int[][] lcs = lcs(oldLines, newLines);

        int i = oldLines.size();
        int j = newLines.size();

        while (i > 0 || j > 0) { // Backtrack from the end
            if (i > 0 && j > 0 && oldLines.get(i - 1).equals(newLines.get(j - 1))) { // Equal lines
                diffs.add(0, new Diff(DiffType.EQUAL, List.of(oldLines.get(i - 1))));
                i--; j--;
            } else { // Difference found
                int endI = i, endJ = j;
                // Find the next common line to define the change block
                while (i > 0 || j > 0) {
                    if (i > 0 && j > 0 && oldLines.get(i - 1).equals(newLines.get(j - 1))) {
                        break; // Found the start of the difference block
                    }
                    if (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j])) {
                        j--;
                    } else if (i > 0 && (j == 0 || lcs[i - 1][j] > lcs[i][j - 1])) {
                        i--;
                    }
                }

                List<String> deleted = new ArrayList<>(oldLines.subList(i, endI));
                List<String> inserted = new ArrayList<>(newLines.subList(j, endJ));

                if (!deleted.isEmpty() && !inserted.isEmpty()) {
                    diffs.add(0, new Diff(DiffType.CHANGE, inserted, deleted));
                } else if (!deleted.isEmpty()) {
                    diffs.add(0, new Diff(DiffType.DELETE, deleted));
                } else if (!inserted.isEmpty()) {
                    diffs.add(0, new Diff(DiffType.INSERT, inserted));
                }
            }
        }
        return merge(diffs);
    }

    private static int[][] lcs(List<String> a, List<String> b) {
        int[][] lengths = new int[a.size() + 1][b.size() + 1];
        for (int i = 0; i < a.size(); i++) {
            for (int j = 0; j < b.size(); j++) {
                if (a.get(i).equals(b.get(j))) {
                    lengths[i + 1][j + 1] = lengths[i][j] + 1;
                } else {
                    lengths[i + 1][j + 1] = Math.max(lengths[i + 1][j], lengths[i][j + 1]);
                }
            }
        }
        return lengths;
    }

    private static List<Diff> merge(List<Diff> diffs) {
        if (diffs.isEmpty()) {
            return diffs;
        }
        List<Diff> merged = new ArrayList<>();
        Diff lastDiff = null;
        for (Diff diff : diffs) {
            if (lastDiff != null && lastDiff.type == diff.type && lastDiff.type != DiffType.CHANGE) {
                List<String> newLines = new ArrayList<>(lastDiff.lines);
                newLines.addAll(diff.lines);
                lastDiff = new Diff(lastDiff.type, newLines);
            } else {
                if (lastDiff != null) {
                    merged.add(lastDiff);
                }
                lastDiff = diff;
            }
        }
        if (lastDiff != null) {
            merged.add(lastDiff);
        }
        return merged;
    }
}