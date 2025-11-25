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
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets; 
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

public class GitDiffViewer extends JFrame {

    private final JTextField repoPathField;
    private final JTextField filePathField;
    private final JList<CommitInfo> commitList;
    private final DefaultListModel<CommitInfo> commitListModel;
    private final JTextPane leftDiffPane;
    private final JTextPane rightDiffPane;
    private final JLabel statusBar;
    private final JSplitPane mainSplitPane;

    private Git git;
    private Repository repository;

    // 差分表示用のスタイル
    private static final Color ADD_COLOR = new Color(220, 255, 220);
    private static final Color DELETE_COLOR = new Color(255, 220, 220);
    private static final Color MODIFIED_COLOR = new Color(220, 220, 255);

    // Preferences keys
    private static final String PREF_REPO_PATH = "repoPath";
    private static final String PREF_FILE_PATH = "filePath";
    private static final String PREF_X = "x";
    private static final String PREF_Y = "y";
    private static final String PREF_WIDTH = "width";
    private static final String PREF_HEIGHT = "height";
    private static final String PREF_DIVIDER_LOCATION = "dividerLocation";
    private final Preferences prefs;

    public GitDiffViewer() {
        setTitle("Git File Diff Viewer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Initialize preferences
        prefs = Preferences.userNodeForPackage(GitDiffViewer.class);
        
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

        mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(commitList), diffSplitPane);
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

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                showDiff(); // Recalculate wrapping on resize
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Save preferences on exit
                prefs.put(PREF_REPO_PATH, repoPathField.getText());
                prefs.put(PREF_FILE_PATH, filePathField.getText());
                prefs.putInt(PREF_X, getX());
                prefs.putInt(PREF_Y, getY());
                prefs.putInt(PREF_WIDTH, getWidth());
                prefs.putInt(PREF_HEIGHT, getHeight());
                prefs.putInt(PREF_DIVIDER_LOCATION, mainSplitPane.getDividerLocation());
            }
        });

        // Restore preferences
        repoPathField.setText(prefs.get(PREF_REPO_PATH, ""));
        filePathField.setText(prefs.get(PREF_FILE_PATH, ""));
        setSize(prefs.getInt(PREF_WIDTH, 1200), prefs.getInt(PREF_HEIGHT, 800));
        setLocation(prefs.getInt(PREF_X, -1), prefs.getInt(PREF_Y, -1));
        if (getX() == -1 && getY() == -1) { // If no location is saved, center the window
            setLocationRelativeTo(null);
        }
        mainSplitPane.setDividerLocation(prefs.getInt(PREF_DIVIDER_LOCATION, 300));
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
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
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

        LineWrapper leftWrapper = new LineWrapper(leftDiffPane);
        LineWrapper rightWrapper = new LineWrapper(rightDiffPane);

        List<DiffUtils.Diff> diffs = DiffUtils.diff(oldLines, newLines);

        for (DiffUtils.Diff diff : diffs) {
            switch (diff.type) {
                case EQUAL:
                    for (int i = 0; i < diff.lines.size(); i++) {
                        appendLines(leftWrapper, rightWrapper, leftDoc, rightDoc, diff.lines.get(i), diff.lines.get(i), plainStyle, plainStyle, leftLineNumbers, rightLineNumbers, leftLine++, rightLine++);
                    }
                    break;
                case DELETE:
                    for (int i = 0; i < diff.lines.size(); i++) {
                        appendLines(leftWrapper, rightWrapper, leftDoc, rightDoc, diff.lines.get(i), "", deleteStyle, plainStyle, leftLineNumbers, rightLineNumbers, leftLine++, null);
                    }
                    break;
                case INSERT:
                    for (int i = 0; i < diff.lines.size(); i++) {
                        appendLines(leftWrapper, rightWrapper, leftDoc, rightDoc, "", diff.lines.get(i), plainStyle, addStyle, leftLineNumbers, rightLineNumbers, null, rightLine++);
                    }
                    break;
                case CHANGE:
                    for (int i = 0; i < Math.max(diff.oldLines.size(), diff.newLines.size()); i++) {
                        String oldLine = i < diff.oldLines.size() ? diff.oldLines.get(i) : "";
                        String newLine = i < diff.newLines.size() ? diff.newLines.get(i) : "";
                        Integer oldLineNum = i < diff.oldLines.size() ? leftLine++ : null;
                        Integer newLineNum = i < diff.newLines.size() ? rightLine++ : null;
                        appendLines(leftWrapper, rightWrapper, leftDoc, rightDoc, oldLine, newLine, modifiedStyle, modifiedStyle, leftLineNumbers, rightLineNumbers, oldLineNum, newLineNum);
                    }
                    break;
            }
        }

        // Update line number views
        JScrollPane leftScrollPane = (JScrollPane) leftDiffPane.getParent().getParent();
        JViewport leftRowHeader = leftScrollPane.getRowHeader();
        if (leftRowHeader != null) {
            Component leftHeaderView = leftRowHeader.getView();
            if (leftHeaderView instanceof LineNumberView) {
                ((LineNumberView) leftHeaderView).setLineNumbers(leftLineNumbers);
            }
        }
        JScrollPane rightScrollPane = (JScrollPane) rightDiffPane.getParent().getParent();
        JViewport rightRowHeader = rightScrollPane.getRowHeader();
        if (rightRowHeader != null) {
            Component rightHeaderView = rightRowHeader.getView();
            if (rightHeaderView instanceof LineNumberView) {
                ((LineNumberView) rightHeaderView).setLineNumbers(rightLineNumbers);
            }
        }

        // スクロール範囲を再計算して、両方のペインが最後までスクロールできるようにする
        // SwingUtilities.invokeLaterを使用して、UIの更新が完了した後に実行する
        SwingUtilities.invokeLater(() -> {
            JScrollPane tmpLeftScrollPane = (JScrollPane) leftDiffPane.getParent().getParent();

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

    private void appendLines(LineWrapper leftWrapper, LineWrapper rightWrapper, StyledDocument leftDoc, StyledDocument rightDoc,
                             String oldLine, String newLine,
                             AttributeSet oldStyle, AttributeSet newStyle,
                             List<Integer> leftLineNumbers, List<Integer> rightLineNumbers,
                             Integer oldLineNum, Integer newLineNum) throws BadLocationException {

        List<String> wrappedOld = leftWrapper.wrap(oldLine);
        List<String> wrappedNew = rightWrapper.wrap(newLine);
        int wrappedLinesCount = Math.max(wrappedOld.size(), wrappedNew.size());

        for (int j = 0; j < wrappedLinesCount; j++) {
            String leftText = j < wrappedOld.size() ? wrappedOld.get(j) : "";
            String rightText = j < wrappedNew.size() ? wrappedNew.get(j) : "";

            // Add line numbers only for the first physical line of a logical line
            leftLineNumbers.add(j == 0 ? oldLineNum : null);
            rightLineNumbers.add(j == 0 ? newLineNum : null);

            leftDoc.insertString(leftDoc.getLength(), leftText + "\n", oldLineNum != null ? oldStyle : null);
            rightDoc.insertString(rightDoc.getLength(), rightText + "\n", newLineNum != null ? newStyle : null);
        }
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

}
