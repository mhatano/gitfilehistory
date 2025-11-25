package jp.hatano.gitfilehistory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * A custom renderer for displaying CommitInfo objects in a JList.
 */
public class CommitCellRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof CommitInfo) {
            CommitInfo info = (CommitInfo) value;
            label.setText(String.format("<html><b>%s</b> - %s<br><font color='gray'>%s by %s</font></html>",
                    info.getShortHash(),
                    info.message,
                    info.date,
                    info.author));
            label.setBorder(new EmptyBorder(5, 5, 5, 5));
        }
        return label;
    }
}