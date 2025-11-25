package jp.hatano.gitfilehistory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A component that displays line numbers for a JTextPane.
 */
public class LineNumberView extends JComponent {
    private static final int MARGIN = 5;
    private final JTextPane textPane;
    private final FontMetrics fontMetrics;
    private List<Integer> lineNumbers;

    public LineNumberView(JTextPane textPane) {
        this.textPane = textPane;
        Font font = textPane.getFont();
        this.fontMetrics = textPane.getFontMetrics(font);
        setFont(font);
        this.lineNumbers = new ArrayList<>();
        setBackground(new Color(240, 240, 240));
        setForeground(Color.GRAY);

        textPane.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { update(); }
            @Override public void removeUpdate(DocumentEvent e) { update(); }
            @Override public void changedUpdate(DocumentEvent e) { update(); }
        });

        textPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                update();
            }
        });
    }

    public void setLineNumbers(List<Integer> lineNumbers) {
        this.lineNumbers = lineNumbers;
        update();
    }

    private void update() {
        // Update the preferred size and repaint
        getParent().revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        int maxNum = lineNumbers.stream()
            .filter(n -> n != null)
            .mapToInt(n -> n)
            .max().orElse(1);
        String maxLineNum = String.valueOf(maxNum);
        int width = fontMetrics.stringWidth(maxLineNum) + 2 * MARGIN;
        return new Dimension(width, textPane.getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(getForeground());

        Rectangle clip = g.getClipBounds();
        int startOffset = textPane.viewToModel2D(new Point(0, clip.y));
        int endOffset = textPane.viewToModel2D(new Point(0, clip.y + clip.height));

        Element root = textPane.getDocument().getDefaultRootElement();

        for (int i = root.getElementIndex(startOffset); i <= root.getElementIndex(endOffset); i++) {
            if (i < 0 || i >= lineNumbers.size()) continue;
            
            Integer lineNumberInt = lineNumbers.get(i);
            if (lineNumberInt == null) continue; // Don't draw number for blank lines

            try {
                String lineNumber = String.valueOf(lineNumberInt);
                Rectangle r = textPane.modelToView2D(root.getElement(i).getStartOffset()).getBounds();
                int y = r.y + r.height - fontMetrics.getDescent();
                int x = getWidth() - fontMetrics.stringWidth(lineNumber) - MARGIN;
                g.drawString(lineNumber, x, y);
            } catch (BadLocationException e) { /* ignore */ }
        }
    }
}