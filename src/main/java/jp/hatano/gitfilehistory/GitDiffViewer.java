package jp.hatano.gitfilehistory;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GitDiffViewer extends JFrame {

    private final JTextField repoPathField;
    private final JTextField filePathField;
    private final JList<CommitInfo> commitList;
    private final DefaultListModel<CommitInfo> commitListModel;
    private final JTextPane leftDiffPane;
    private final JTextPane rightDiffPane;
    private final JLabel statusBar;

    private Git git;
    private Repository repository;

    // 差分表示用のスタイル
    private static final Color ADD_COLOR = new Color(220, 255, 220);
    private static final Color DELETE_COLOR = new Color(255, 220, 220);
    private static final Color MODIFIED_COLOR = new Color(220, 220, 255);

    public GitDiffViewer() {
        setTitle("Git File Diff Viewer");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // --- UIコンポーネントの初期化 ---

        // 上部パネル (入力フィールドとボタン)
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        JPanel pathPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        pathPanel.add(new JLabel("Repo Path:"), gbc);
        repoPathField = new JTextField(40);
        gbc.gridx = 1; gbc.weightx = 1.0;
        pathPanel.add(repoPathField, gbc);
        JButton browseRepoButton = new JButton("...");
        gbc.gridx = 2; gbc.weightx = 0;
        pathPanel.add(browseRepoButton, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        pathPanel.add(new JLabel("File Path:"), gbc);
        filePathField = new JTextField(40);
        gbc.gridx = 1; gbc.weightx = 1.0;
        pathPanel.add(filePathField, gbc);
        JButton browseFileButton = new JButton("...");
        gbc.gridx = 2; gbc.weightx = 0;
        pathPanel.add(browseFileButton, gbc);

        JButton loadCommitsButton = new JButton("Load Commits");
        
        topPanel.add(pathPanel, BorderLayout.CENTER);
        topPanel.add(loadCommitsButton, BorderLayout.EAST);

        // 中央パネル (コミットリストと差分表示)
        commitListModel = new DefaultListModel<>();
        commitList = new JList<>(commitListModel);
        commitList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        commitList.setCellRenderer(new CommitCellRenderer());

        // 左右のペインを同期スクロールさせる
        JScrollPane leftScrollPane = createDiffScrollPane();
        JScrollPane rightScrollPane = createDiffScrollPane();
        // JScrollPaneからJTextPaneを取得
        leftDiffPane = (JTextPane) leftScrollPane.getViewport().getView();
        rightDiffPane = (JTextPane) rightScrollPane.getViewport().getView();

        
        // 1つのモデルを共有することで、完全に同期したスクロールを実現する
        BoundedRangeModel sharedModel = new DefaultBoundedRangeModel();
        leftScrollPane.getVerticalScrollBar().setModel(sharedModel);
        rightScrollPane.getVerticalScrollBar().setModel(sharedModel);

        JSplitPane diffSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScrollPane, rightScrollPane);
        diffSplitPane.setResizeWeight(0.5);

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(commitList), diffSplitPane);
        mainSplitPane.setResizeWeight(0.3);

        // 下部ステータスバー
        statusBar = new JLabel("Ready");
        statusBar.setBorder(new EmptyBorder(2, 5, 2, 5));

        // --- レイアウトへの追加 ---
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(topPanel, BorderLayout.NORTH);
        contentPane.add(mainSplitPane, BorderLayout.CENTER);
        contentPane.add(statusBar, BorderLayout.SOUTH);

        // --- イベントリスナーの設定 ---
        browseRepoButton.addActionListener(e -> browseForDirectory(repoPathField));
        browseFileButton.addActionListener(e -> browseForFile(filePathField));
        loadCommitsButton.addActionListener(e -> loadCommits());
        commitList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDiff();
            }
        });
    }

    private JScrollPane createDiffScrollPane() { // Will be used for left pane
        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textPane.setMargin(new Insets(5, 5, 5, 5));
        // The LineNumberView will be created with a reference to the text pane
        // and will track line numbers.
        // We will pass the initial line number list later.
        // For now, we just create it.

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setRowHeaderView(new LineNumberView(textPane));
        return scrollPane;
    }

    private void browseForDirectory(JTextField targetField) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Git Repository Directory");
        if (targetField.getText() != null && !targetField.getText().isEmpty()) {
            chooser.setCurrentDirectory(new File(targetField.getText()));
        }
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            targetField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void browseForFile(JTextField targetField) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setDialogTitle("Select File in Repository");
        if (repoPathField.getText() != null && !repoPathField.getText().isEmpty()) {
            chooser.setCurrentDirectory(new File(repoPathField.getText()));
        }
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            // リポジトリからの相対パスに変換
            File repoDir = new File(repoPathField.getText());
            String relativePath = repoDir.toURI().relativize(chooser.getSelectedFile().toURI()).getPath();
            targetField.setText(relativePath.replace(File.separatorChar, '/'));
        }
    }

    private void loadCommits() {
        String repoPath = repoPathField.getText();
        String filePath = filePathField.getText();

        if (repoPath.isEmpty() || filePath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Repository path and file path must be specified.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        commitListModel.clear();
        leftDiffPane.setText("");
        rightDiffPane.setText("");
        statusBar.setText("Loading commits...");

        try {
            File repoDir = new File(repoPath, ".git");
            if (!repoDir.exists()) {
                throw new IOException("'.git' directory not found. Please select the root of the repository.");
            }
            
            repository = new FileRepositoryBuilder().setGitDir(repoDir).readEnvironment().findGitDir().build();
            git = new Git(repository);

            // ブランチをまたがってファイルのコミット履歴を取得
            Iterable<RevCommit> logs = git.log().addPath(filePath).call();
            List<CommitInfo> commits = new ArrayList<>();
            for (RevCommit rev : logs) {
                commits.add(new CommitInfo(rev));
            }
            
            if (commits.isEmpty()) {
                statusBar.setText("No commits found for this file.");
            } else {
                commits.forEach(commitListModel::addElement);
                statusBar.setText(commits.size() + " commits loaded.");
            }

        } catch (IOException | GitAPIException e) {
            handleException("Error loading commits", e);
        }
    }

    private void showDiff() {
        List<CommitInfo> selectedCommits = commitList.getSelectedValuesList();
        if (selectedCommits.size() != 2) {
            leftDiffPane.setText("");
            rightDiffPane.setText("");
            return;
        }

        // コミットを時系列順に並べる (古い方が first)
        CommitInfo first = selectedCommits.get(0).getCommit().getCommitTime() < selectedCommits.get(1).getCommit().getCommitTime() ? selectedCommits.get(0) : selectedCommits.get(1);
        CommitInfo second = first == selectedCommits.get(0) ? selectedCommits.get(1) : selectedCommits.get(0);

        statusBar.setText("Generating diff between " + first.getShortHash() + " and " + second.getShortHash());
        leftDiffPane.setText("");
        rightDiffPane.setText("");
        leftDiffPane.getStyledDocument().putProperty("IgnoreCharset", Boolean.TRUE);
        rightDiffPane.getStyledDocument().putProperty("IgnoreCharset", Boolean.TRUE);

        try {
            String filePath = filePathField.getText();
            String oldContent = getFileContent(first.getCommit().getId(), filePath);
            String newContent = getFileContent(second.getCommit().getId(), filePath);

            if (oldContent.equals(newContent)) {
                 statusBar.setText("No difference found for the file in selected commits.");
                 leftDiffPane.setText(oldContent);
                 rightDiffPane.setText(newContent);
                return;
            }
            displaySideBySideDiff(oldContent, newContent);
            statusBar.setText("Diff loaded successfully.");

        } catch (Exception e) {
            handleException("Error generating diff", e);
        }
    }
    
    private String getFileContent(ObjectId commitId, String filePath) throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            org.eclipse.jgit.treewalk.TreeWalk treeWalk = org.eclipse.jgit.treewalk.TreeWalk.forPath(repository, filePath, tree);

            if (treeWalk != null) {
                ObjectId objectId = treeWalk.getObjectId(0);
                byte[] bytes = repository.open(objectId).getBytes();
                return new String(bytes, StandardCharsets.UTF_8);
            }
        }
        return ""; // ファイルが存在しない場合
    }

    private void displaySideBySideDiff(String oldText, String newText) throws BadLocationException {
        StyledDocument leftDoc = leftDiffPane.getStyledDocument();
        StyledDocument rightDoc = rightDiffPane.getStyledDocument();
        leftDoc.remove(0, leftDoc.getLength());
        rightDoc.remove(0, rightDoc.getLength());

        SimpleAttributeSet plainStyle = new SimpleAttributeSet();
        SimpleAttributeSet addStyle = new SimpleAttributeSet();
        StyleConstants.setBackground(addStyle, ADD_COLOR);
        SimpleAttributeSet deleteStyle = new SimpleAttributeSet();
        StyleConstants.setBackground(deleteStyle, DELETE_COLOR);
        SimpleAttributeSet modifiedStyle = new SimpleAttributeSet();
        StyleConstants.setBackground(modifiedStyle, MODIFIED_COLOR);

        List<Integer> leftLineNumbers = new ArrayList<>();
        List<Integer> rightLineNumbers = new ArrayList<>();
        int leftLine = 1, rightLine = 1;

        List<String> oldLines = Arrays.asList(oldText.split("\n"));
        List<String> newLines = Arrays.asList(newText.split("\n"));

        List<DiffUtils.Diff> diffs = DiffUtils.diff(oldLines, newLines);

        for (DiffUtils.Diff diff : diffs) {
            String text = String.join("\n", diff.lines) + "\n";
            switch (diff.type) {
                case EQUAL:
                    leftDoc.insertString(leftDoc.getLength(), text, plainStyle);
                    for (int i = 0; i < diff.lines.size(); i++) leftLineNumbers.add(leftLine++);
                    rightDoc.insertString(rightDoc.getLength(), text, plainStyle);
                    for (int i = 0; i < diff.lines.size(); i++) rightLineNumbers.add(rightLine++);
                    break;
                case DELETE:
                    leftDoc.insertString(leftDoc.getLength(), text, deleteStyle);
                    for (int i = 0; i < diff.lines.size(); i++) {
                        leftLineNumbers.add(leftLine++);
                        rightDoc.insertString(rightDoc.getLength(), "\n", plainStyle);
                        rightLineNumbers.add(null); // Placeholder for blank line
                    }
                    break;
                case INSERT:
                    for (int i = 0; i < diff.lines.size(); i++) {
                        leftDoc.insertString(leftDoc.getLength(), "\n", plainStyle);
                        leftLineNumbers.add(null); // Placeholder for blank line
                    }
                    rightDoc.insertString(rightDoc.getLength(), text, addStyle);
                    for (int i = 0; i < diff.lines.size(); i++) rightLineNumbers.add(rightLine++);
                    break;
                case CHANGE:
                    int oldLinesCount = diff.oldLines.size();
                    int newLinesCount = diff.newLines.size();
                    int maxLines = Math.max(oldLinesCount, newLinesCount);

                    for (int i = 0; i < maxLines; i++) {
                        String oldLine = i < oldLinesCount ? diff.oldLines.get(i) + "\n" : "\n";
                        String newLine = i < newLinesCount ? diff.newLines.get(i) + "\n" : "\n";
                        leftDoc.insertString(leftDoc.getLength(), oldLine, i < oldLinesCount ? modifiedStyle : plainStyle);
                        rightDoc.insertString(rightDoc.getLength(), newLine, i < newLinesCount ? modifiedStyle : plainStyle);
                        leftLineNumbers.add(i < oldLinesCount ? leftLine++ : null);
                        rightLineNumbers.add(i < newLinesCount ? rightLine++ : null);
                    }
                    break;
            }
        }

        // Update line number views
        JScrollPane leftScrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, leftDiffPane);
        ((LineNumberView) leftScrollPane.getRowHeader().getView()).setLineNumbers(leftLineNumbers);
        JScrollPane rightScrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, rightDiffPane);
        ((LineNumberView) rightScrollPane.getRowHeader().getView()).setLineNumbers(rightLineNumbers);

        // スクロール範囲を再計算して、両方のペインが最後までスクロールできるようにする
        // SwingUtilities.invokeLaterを使用して、UIの更新が完了した後に実行する
        SwingUtilities.invokeLater(() -> {
            JScrollPane tmpLeftScrollPane = (JScrollPane) leftDiffPane.getParent().getParent();
            JScrollPane tmpRightScrollPane = (JScrollPane) rightDiffPane.getParent().getParent();

            BoundedRangeModel model = tmpLeftScrollPane.getVerticalScrollBar().getModel();
            int extent = model.getExtent();
            int leftHeight = leftDiffPane.getPreferredSize().height;
            int rightHeight = rightDiffPane.getPreferredSize().height;
            int max = Math.max(leftHeight, rightHeight);
            model.setRangeProperties(model.getValue(), extent, model.getMinimum(), max, false);
        });

        leftDiffPane.setCaretPosition(0);
        rightDiffPane.setCaretPosition(0);
    }

    private void handleException(String message, Exception e) {
        e.printStackTrace();
        statusBar.setText("Error: " + e.getMessage());
        JOptionPane.showMessageDialog(this, message + ":\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }

    // --- mainメソッド ---
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new GitDiffViewer().setVisible(true);
        });
    }

    // --- 内部クラス ---

    /**
     * コミット情報を保持するクラス
     */
    private static class CommitInfo {
        private final RevCommit commit;
        private final String shortHash;
        private final String author;
        private final String date;
        private final String message;

        public CommitInfo(RevCommit commit) {
            this.commit = commit;
            this.shortHash = commit.getId().abbreviate(7).name();
            this.author = commit.getAuthorIdent().getName();
            this.date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(commit.getAuthorIdent().getWhen());
            this.message = commit.getShortMessage();
        }

        public RevCommit getCommit() {
            return commit;
        }

        public String getShortHash() {
            return shortHash;
        }

        @Override
        public String toString() {
            return String.format("%s - %s (%s)", shortHash, message, author);
        }
    }

    /**
     * JListのセルをカスタム描画するレンダラー
     */
    private static class CommitCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof CommitInfo) {
                CommitInfo info = (CommitInfo) value;
                label.setText(String.format("<html><b>%s</b> - %s<br><font color='gray'>%s by %s</font></html>",
                        info.shortHash,
                        info.message,
                        info.date,
                        info.author));
                label.setBorder(new EmptyBorder(5, 5, 5, 5));
            }
            return label;
        }
    }

    /**
     * A simple diff utility class to find differences between two lists of strings.
     * Uses a basic Longest Common Subsequence (LCS) approach.
     */
    private static class DiffUtils {
        enum DiffType { EQUAL, DELETE, INSERT, CHANGE }
        static final String CHANGE_SEPARATOR = "<-CHANGE->";

        static class Diff {
            final DiffType type;
            final List<String> lines;
            final List<String> oldLines; // For CHANGE type
            final List<String> newLines; // For CHANGE type

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

    /**
     * A component that displays line numbers for a JTextPane.
     */
    private static class LineNumberView extends JComponent {
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
}
