package jp.hatano.gitfilehistory;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A helper class to manually wrap text based on component width.
 */
public class LineWrapper {
    private final FontMetrics fontMetrics;
    private final int wrapWidth;

    public LineWrapper(JTextPane textPane) {
        this.fontMetrics = textPane.getFontMetrics(textPane.getFont());
        // Calculate available width, considering margins
        Insets insets = textPane.getInsets();
        this.wrapWidth = textPane.getWidth() - insets.left - insets.right;
    }

    public List<String> wrap(String text) {
        List<String> wrappedLines = new ArrayList<>();
        if (text.isEmpty() || wrapWidth <= 0) {
            wrappedLines.add(text);
            return wrappedLines;
        }

        // Find indentation and the rest of the text
        int indentLength = 0;
        while (indentLength < text.length() && Character.isWhitespace(text.charAt(indentLength))) {
            indentLength++;
        }
        String indent = text.substring(0, indentLength);
        String content = text.substring(indentLength);

        StringBuilder currentLine = new StringBuilder(indent);
        String[] words = content.split(" ", -1);

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            // Check if the word itself is too long, or if adding it exceeds width
            if (currentLine.length() > indent.length() && fontMetrics.stringWidth(currentLine.toString() + " " + word) > wrapWidth) {
                wrappedLines.add(currentLine.toString());
                currentLine = new StringBuilder(indent).append(word);
            } else {
                if (currentLine.length() > indent.length()) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            }
        }
        wrappedLines.add(currentLine.toString());

        return wrappedLines;
    }
}