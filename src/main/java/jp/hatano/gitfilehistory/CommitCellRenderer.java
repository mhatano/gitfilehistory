/*
 * Copyright (c) 2026 jp.hatano.gitfilehistory
 *
 * Licensed under the MIT License. See LICENSE.md in project root for details.
 */
package jp.hatano.gitfilehistory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * A custom renderer for displaying CommitInfo objects in a JList.
 */
public class CommitCellRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
            boolean cellHasFocus) {
        JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof CommitInfo) {
            CommitInfo info = (CommitInfo) value;
            StringBuilder branchesBuilder = new StringBuilder();
            if (info.branchNames != null && !info.branchNames.isEmpty()) {
                for (String branch : info.branchNames) {
                    // ブランチ名を分かりやすいタグ（バッジ）形式でスタイリング
                    branchesBuilder.append(String.format(
                            "&nbsp;<span style='background-color:#E0F7FA; color:#006064;'><b>[%s]</b></span>", branch));
                }
            }
            label.setText(String.format(
                    "<html><b style='font-family: monospace;'>%s</b> - %s%s<br><font color='gray'>%s by %s</font></html>",
                    info.getShortHash(),
                    info.message,
                    branchesBuilder.toString(),
                    info.date,
                    info.author));
            label.setBorder(new EmptyBorder(5, 5, 5, 5));
        }
        return label;
    }
}