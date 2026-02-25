/*
 * Copyright (c) 2026 jp.hatano.gitfilehistory
 *
 * Licensed under the MIT License. See LICENSE.md in project root for details.
 */
package jp.hatano.gitfilehistory;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.concurrent.ExecutionException;

public class GitDiffViewer extends JFrame {

    private static Logger logger;

    private final JTextField repoPathField;
    private final JTextField filePathField;
    private final JList<CommitInfo> commitList;
    private final DefaultListModel<CommitInfo> commitListModel;
    private final JTextPane leftDiffPane;
    private final JTextPane rightDiffPane;
    private final JLabel statusBar;
    private final JComboBox<String> encodingComboBox;
    private final JSplitPane mainSplitPane;

    private Git git;
    private Repository repository;

    // 差分表示用のスタイル
    private static final Color ADD_COLOR = new Color(220, 255, 220);
    private static final Color DELETE_COLOR = new Color(255, 220, 220);
    private static final Color MODIFIED_COLOR = new Color(220, 220, 255);

    // 検索機能用UI
    private JTextField searchField;
    private JCheckBox caseCheckBox;
    private JCheckBox regexCheckBox;

    // ハイライト関連
    private final Highlighter.HighlightPainter searchHighlightPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 255, 0, 128));
    private final Highlighter.HighlightPainter currentHighlightPainter = new DefaultHighlighter.DefaultHighlightPainter(Color.ORANGE);
    private final List<HighlightInfo> highlights = new ArrayList<>();
    private int currentHighlightIndex = -1;

    // ハイライトされた箇所の情報を保持するインナークラス
    private static class HighlightInfo {
        final JTextPane pane;
        final int start;
        final int end;
        Object tag; // Highlighter.addHighlightから返されるタグを保持

        HighlightInfo(JTextPane pane, int start, int end) {
            this.pane = pane;
            this.start = start;
            this.end = end;
        }
    }

    // Preferences keys
    private static final String PREF_REPO_PATH = "repoPath";
    private static final String PREF_FILE_PATH = "filePath";
    private static final String PREF_X = "x";
    private static final String PREF_Y = "y";
    private static final String PREF_WIDTH = "width";
    private static final String PREF_HEIGHT = "height";
    private static final String PREF_DIVIDER_LOCATION = "dividerLocation";
    private static final String PREF_ENCODING = "encoding";
    private final Preferences prefs;
    private final JButton loadCommitsButton;

    // 差分結果のキャッシュ
    private List<DiffUtils.Diff> cachedDiffs;
    private String cachedOldContent;
    private String cachedNewContent;
    private CommitInfo cachedFirstCommit;
    private CommitInfo cachedSecondCommit;

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

        gbc.gridx = 0;
        gbc.gridy = 0;
        pathPanel.add(new JLabel("Repo Path:"), gbc);
        repoPathField = new JTextField(40);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        pathPanel.add(repoPathField, gbc);
        JButton browseRepoButton = new JButton("...");
        gbc.gridx = 2;
        gbc.weightx = 0;
        pathPanel.add(browseRepoButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        pathPanel.add(new JLabel("File Path:"), gbc);
        filePathField = new JTextField(40);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        pathPanel.add(filePathField, gbc);
        JButton browseFileButton = new JButton("...");
        gbc.gridx = 2;
        gbc.weightx = 0;
        pathPanel.add(browseFileButton, gbc);

        loadCommitsButton = new JButton("Load Commits");

        JPanel rightTopPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton exportHtmlButton = new JButton("Export HTML");
        exportHtmlButton.addActionListener(e -> exportHtml());
        JButton exportPatchButton = new JButton("Export Patch");
        exportPatchButton.addActionListener(e -> exportPatch());

        encodingComboBox = new JComboBox<>(new String[] { "UTF-8", "Shift_JIS", "EUC-JP" });
        rightTopPanel.add(new JLabel("Encoding:"));
        rightTopPanel.add(encodingComboBox);
        rightTopPanel.add(exportHtmlButton);
        rightTopPanel.add(exportPatchButton);
        rightTopPanel.add(loadCommitsButton);

        topPanel.add(pathPanel, BorderLayout.CENTER);
        topPanel.add(rightTopPanel, BorderLayout.EAST);

        // 中央パネル (コミットリストと差分表示)
        commitListModel = new DefaultListModel<>();
        commitList = new JList<>(commitListModel);
        commitList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        commitList.setCellRenderer(new CommitCellRenderer());

        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem copyHashItem = new JMenuItem("Copy Commit Hash");
        copyHashItem.addActionListener(e -> {
            CommitInfo selected = commitList.getSelectedValue();
            if (selected != null && !selected.isUncommitted()) {
                String hash = selected.getCommit().getId().name();
                java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(hash);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            }
        });
        JMenuItem copyBranchNamesItem = new JMenuItem("Copy Branch Name(s)");
        copyBranchNamesItem.addActionListener(e -> {
            CommitInfo selected = commitList.getSelectedValue();
            if (selected != null && !selected.isUncommitted() && selected.branchNames != null && !selected.branchNames.isEmpty()) {
                String branchNames = String.join(", ", selected.branchNames);
                java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(branchNames);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            }
        });
        JMenuItem copyCommitDateItem = new JMenuItem("Copy Commit Date");
        copyCommitDateItem.addActionListener(e -> {
            CommitInfo selected = commitList.getSelectedValue();
            if (selected != null && !selected.isUncommitted()) {
                String date = selected.date;
                java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(date);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            }
        });
        JMenuItem copyAuthorNameItem = new JMenuItem("Copy Author Name");
        copyAuthorNameItem.addActionListener(e -> {
            CommitInfo selected = commitList.getSelectedValue();
            if (selected != null) { // Uncommitted changes also have an author (Local Workspace)
                String author = selected.author;
                java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(author);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            }
        });
        JMenuItem copyMessageItem = new JMenuItem("Copy Commit Message");
        copyMessageItem.addActionListener(e -> {
            CommitInfo selected = commitList.getSelectedValue();
            if (selected != null) {
                String message = selected.message;
                java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(message);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            }
        });
        JMenuItem copyJsonItem = new JMenuItem("Copy as JSON");
        copyJsonItem.addActionListener(e -> {
            CommitInfo selected = commitList.getSelectedValue();
            if (selected != null) {
                StringBuilder json = new StringBuilder();
                json.append("{\n");
                json.append("  \"hash\": \"").append(selected.getShortHash()).append("\",\n");
                json.append("  \"date\": \"").append(selected.date).append("\",\n");
                json.append("  \"author\": \"").append(selected.author).append("\",\n");
                json.append("  \"message\": \"").append(selected.message).append("\",\n");
                json.append("  \"branches\": [");
                json.append(selected.branchNames != null ? String.join(", ", selected.branchNames.stream().map(s -> "\"" + s + "\"").collect(java.util.stream.Collectors.toList())) : "");
                json.append("]\n");
                json.append("}");
                java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(json.toString());
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            }
        });
        popupMenu.add(copyHashItem);

        commitList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int index = commitList.locationToIndex(e.getPoint());
                    if (index != -1 && commitList.getCellBounds(index, index).contains(e.getPoint())) {
                        if (!commitList.isSelectedIndex(index)) {
                            commitList.setSelectedIndex(index);
                        }
                        popupMenu.show(commitList, e.getX(), e.getY());
                    }
                }
            }
        });
        popupMenu.add(copyBranchNamesItem);
        popupMenu.add(copyCommitDateItem);
        popupMenu.add(copyAuthorNameItem);
        popupMenu.add(copyMessageItem);
        popupMenu.add(copyJsonItem);

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

        // --- メニューバー (ヘルプ/About) ---
        JMenuBar menuBar = new JMenuBar();
        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);

        // --- レイアウトへの追加 ---
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(topPanel, BorderLayout.NORTH);
        contentPane.add(mainSplitPane, BorderLayout.CENTER);

        // 検索パネルとステータスバーをまとめる下部パネル
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(createSearchPanel(), BorderLayout.NORTH);
        southPanel.add(statusBar, BorderLayout.SOUTH);
        contentPane.add(southPanel, BorderLayout.SOUTH);

        // --- イベントリスナーの設定 ---
        browseRepoButton.addActionListener(e -> browseForDirectory(repoPathField));
        browseFileButton.addActionListener(e -> browseForFile(filePathField));
        loadCommitsButton.addActionListener(e -> loadCommits());
        encodingComboBox.addActionListener(e -> {
            if (commitList.getSelectedIndices().length == 2) {
                calculateAndShowDiff(); // Recalculate with new encoding
            }
        });
        commitList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                calculateAndShowDiff(); // New selection, so recalculate
            }
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                redisplayDiff(); // Just re-render with cached diff
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
                prefs.put(PREF_ENCODING, (String) encodingComboBox.getSelectedItem());
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
        encodingComboBox.setSelectedItem(prefs.get(PREF_ENCODING, "UTF-8"));
    }

    /**
     * Show an about dialog containing copyright and license information.
     */
    private void showAboutDialog() {
        String message = "Git File Diff Viewer\n"
                + "Copyright (c) 2026 jp.hatano.gitfilehistory\n"
                + "Licensed under the MIT License\n\n"
                + "Dependencies and their licenses:\n"
                + "  - JGit (EPL-1.0)\n"
                + "  - SLF4J (MIT)\n"
                + "  - Logback (EPL-1.0 / LGPL2.1)\n"
                + "  - java-diff-utils (MIT)\n"
                + "  - JUnit (EPL-1.0)\n";
        JOptionPane.showMessageDialog(this, message, "About", JOptionPane.INFORMATION_MESSAGE);
    }

    private JPanel createSearchPanel() {
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        searchPanel.setBorder(new EmptyBorder(2, 2, 2, 2));
        searchPanel.add(new JLabel("Search:"));
        searchField = new JTextField(25);
        caseCheckBox = new JCheckBox("Ignore Case", true);
        regexCheckBox = new JCheckBox("Regex");
        JButton prevButton = new JButton("< Prev");
        JButton nextButton = new JButton("Next >");

        searchPanel.add(searchField);
        searchPanel.add(caseCheckBox);
        searchPanel.add(regexCheckBox);
        searchPanel.add(prevButton);
        searchPanel.add(nextButton);

        // イベントリスナー
        DocumentListener updateListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateHighlights(); }
            @Override public void removeUpdate(DocumentEvent e) { updateHighlights(); }
            @Override public void changedUpdate(DocumentEvent e) { updateHighlights(); }
        };

        searchField.getDocument().addDocumentListener(updateListener);
        caseCheckBox.addActionListener(e -> updateHighlights());
        regexCheckBox.addActionListener(e -> updateHighlights());

        nextButton.addActionListener(e -> navigateHighlights(true));
        prevButton.addActionListener(e -> navigateHighlights(false));
        searchField.addActionListener(e -> navigateHighlights(true)); // Enterで次へ

        return searchPanel;
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
            JOptionPane.showMessageDialog(this, "Repository path and file path must be specified.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        commitListModel.clear();
        // Also clear the diff cache
        cachedDiffs = null;
        cachedOldContent = null;
        cachedNewContent = null;
        cachedFirstCommit = null;
        cachedSecondCommit = null;

        clearHighlights(); // ハイライトをクリア
        if (searchField != null) searchField.setText(""); // 検索フィールドをクリア
        leftDiffPane.setText("");
        rightDiffPane.setText("");
        statusBar.setText("Loading commits...");

        try {
            final File repoDir = new File(repoPath, ".git");
            if (!repoDir.exists()) {
                throw new IOException("'.git' directory not found. Please select the root of the repository.");
            }
        } catch (IOException e) {
            handleException("Error loading commits", e);
            return;
        }

        loadCommitsButton.setEnabled(false);
        statusBar.setText("Loading commits...");

        SwingWorker<List<CommitInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<CommitInfo> doInBackground() throws Exception {
                File repoDir = new File(repoPath, ".git");
                repository = new FileRepositoryBuilder().setGitDir(repoDir).readEnvironment().findGitDir().build();
                git = new Git(repository);

                // Create a map from commit ID to branch names for efficiency (Local branches
                // only)
                Map<ObjectId, List<String>> commitToBranchesMap = new HashMap<>();
                List<Ref> branches = git.branchList().call(); // ローカルブランチのみを取得
                try (RevWalk revWalk = new RevWalk(repository)) {
                    for (Ref branch : branches) {
                        RevCommit commit = revWalk.parseCommit(branch.getObjectId());
                        revWalk.markStart(commit);
                        for (RevCommit currentCommit : revWalk) {
                            commitToBranchesMap.computeIfAbsent(currentCommit.getId(), k -> new ArrayList<>())
                                    .add(Repository.shortenRefName(branch.getName()));
                        }
                        revWalk.reset();
                    }
                }

                // ブランチをまたがってファイルのコミット履歴を取得
                org.eclipse.jgit.api.LogCommand logCmd = git.log().addPath(filePath);
                for (Ref branch : branches) {
                    logCmd.add(branch.getObjectId()); // 各ローカルブランチをログ取得の起点に追加
                }
                Iterable<RevCommit> logs = logCmd.call();

                List<CommitInfo> commits = new ArrayList<>();
                for (RevCommit rev : logs) {
                    List<String> branchNames = commitToBranchesMap.getOrDefault(rev.getId(), Collections.emptyList());
                    commits.add(new CommitInfo(rev, branchNames));
                }

                // 時系列順（新しい順）にソートして表示
                commits.sort((c1, c2) -> Integer.compare(c2.getCommit().getCommitTime(), c1.getCommit().getCommitTime()));

                if (!commits.isEmpty()) {
                    // ファイルシステム上の未コミットの変更があるかチェック
                    try {
                        String latestCommitContent = getFileContent(commits.get(0).getCommit().getId(), filePath);
                        File localFile = new File(repoPath, filePath);
                        if (localFile.exists()) {
                            String charsetName = (String) encodingComboBox.getSelectedItem();
                            Charset charset = Charset.forName(charsetName != null ? charsetName : "UTF-8");
                            String localContent = new String(java.nio.file.Files.readAllBytes(localFile.toPath()),
                                    charset);
                            if (!localContent.equals(latestCommitContent)) {
                                // 未コミットの変更がある場合、一番上に追加
                                String nowStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                        .format(new java.util.Date());
                                CommitInfo uncommitted = new CommitInfo("Uncommitted Changes", "Local Workspace", nowStr);
                                commits.add(0, uncommitted);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to check uncommitted changes.", e);
                    }
                }
                return commits;
            }

            @Override
            protected void done() {
                try {
                    List<CommitInfo> commits = get();
                    if (commits.isEmpty()) {
                        statusBar.setText("No commits found for this file.");
                    } else {
                        commits.forEach(commitListModel::addElement);
                        statusBar.setText(commits.size() + " commits loaded.");
                    }
                } catch (InterruptedException | ExecutionException e) {
                    handleException("Error loading commits", (Exception) e.getCause());
                } finally {
                    loadCommitsButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void calculateAndShowDiff() {
        List<CommitInfo> selectedCommits = commitList.getSelectedValuesList();
        clearHighlights(); // 新しいdiffを表示する前にハイライトをクリア

        if (selectedCommits.size() != 2) {
            // Clear panes and cache
            leftDiffPane.setText("");
            rightDiffPane.setText("");
            cachedDiffs = null;
            cachedOldContent = null;
            cachedNewContent = null;
            cachedFirstCommit = null;
            cachedSecondCommit = null;
            return;
        }

        // コミットを時系列順に並べる (古い方が first)
        CommitInfo c1 = selectedCommits.get(0);
        CommitInfo c2 = selectedCommits.get(1);

        long time1 = c1.isUncommitted() ? Long.MAX_VALUE : c1.getCommit().getCommitTime();
        long time2 = c2.isUncommitted() ? Long.MAX_VALUE : c2.getCommit().getCommitTime();

        CommitInfo first, second;
        if (time1 < time2) {
            first = c1;
            second = c2;
        } else {
            first = c2;
            second = c1;
        }

        statusBar.setText("Generating diff between " + first.getShortHash() + " and " + second.getShortHash());

        try {
            String repoPath = repoPathField.getText();
            String filePath = filePathField.getText();
            String oldContent = getFileContent(first, repoPath, filePath);
            String newContent = getFileContent(second, repoPath, filePath);

            // Cache the results
            this.cachedFirstCommit = first;
            this.cachedSecondCommit = second;
            this.cachedOldContent = oldContent;
            this.cachedNewContent = newContent;
            List<String> oldLines = oldContent.isEmpty() ? Collections.emptyList() : Arrays.asList(oldContent.split("\\r?\\n"));
            List<String> newLines = newContent.isEmpty() ? Collections.emptyList() : Arrays.asList(newContent.split("\\r?\\n"));
            this.cachedDiffs = DiffUtils.diff(oldLines, newLines);

            // Now display it using the new redisplay method
            redisplayDiff();

        } catch (Exception e) {
            handleException("Error generating diff", e);
            // Clear cache on error
            cachedDiffs = null;
        }
    }

    private void redisplayDiff() {
        if (cachedDiffs == null) {
            // This can happen on resize before a selection is made.
            // If no selection, ensure panes are empty.
            if (commitList.getSelectedIndices().length != 2) {
                leftDiffPane.setText("");
                rightDiffPane.setText("");
            }
            return;
        }

        statusBar.setText("Rendering diff...");
        try {
            displaySideBySideDiff(cachedOldContent, cachedNewContent, cachedDiffs);
            statusBar.setText(cachedOldContent.equals(cachedNewContent) ? "No difference found." : "Diff loaded successfully.");
        } catch (Exception e) {
            handleException("Error displaying diff", e);
        }
    }

    private String getFileContent(CommitInfo info, String repoPath, String filePath) throws IOException {
        if (info.isUncommitted()) {
            File localFile = new File(repoPath, filePath);
            if (localFile.exists()) {
                String charsetName = (String) encodingComboBox.getSelectedItem();
                Charset charset = Charset.forName(charsetName != null ? charsetName : "UTF-8");
                return new String(java.nio.file.Files.readAllBytes(localFile.toPath()), charset);
            }
            return "";
        }
        return getFileContent(info.getCommit().getId(), filePath);
    }

    private String getFileContent(ObjectId commitId, String filePath) throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            org.eclipse.jgit.treewalk.TreeWalk treeWalk = org.eclipse.jgit.treewalk.TreeWalk.forPath(repository,
                    filePath, tree);

            if (treeWalk != null) {
                ObjectId objectId = treeWalk.getObjectId(0);
                byte[] bytes = repository.open(objectId).getBytes();
                String charsetName = (String) encodingComboBox.getSelectedItem();
                Charset charset = Charset.forName(charsetName != null ? charsetName : "UTF-8");
                return new String(bytes, charset);
            }
        }
        return ""; // ファイルが存在しない場合
    }

    private void displaySideBySideDiff(String oldText, String newText, List<DiffUtils.Diff> diffs) throws BadLocationException {
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

        LineWrapper leftWrapper = new LineWrapper(leftDiffPane);
        LineWrapper rightWrapper = new LineWrapper(rightDiffPane);

        for (DiffUtils.Diff diff : diffs) {
            switch (diff.type) {
                case EQUAL:
                    for (int i = 0; i < diff.lines.size(); i++) {
                        appendLines(leftWrapper, rightWrapper, leftDoc, rightDoc, diff.lines.get(i), diff.lines.get(i),
                                plainStyle, plainStyle, leftLineNumbers, rightLineNumbers, leftLine++, rightLine++);
                    }
                    break;
                case DELETE:
                    for (int i = 0; i < diff.lines.size(); i++) {
                        appendLines(leftWrapper, rightWrapper, leftDoc, rightDoc, diff.lines.get(i), "", deleteStyle,
                                plainStyle, leftLineNumbers, rightLineNumbers, leftLine++, null);
                    }
                    break;
                case INSERT:
                    for (int i = 0; i < diff.lines.size(); i++) {
                        appendLines(leftWrapper, rightWrapper, leftDoc, rightDoc, "", diff.lines.get(i), plainStyle,
                                addStyle, leftLineNumbers, rightLineNumbers, null, rightLine++);
                    }
                    break;
                case CHANGE:
                    for (int i = 0; i < Math.max(diff.oldLines.size(), diff.newLines.size()); i++) {
                        String oldLine = i < diff.oldLines.size() ? diff.oldLines.get(i) : "";
                        String newLine = i < diff.newLines.size() ? diff.newLines.get(i) : "";
                        Integer oldLineNum = i < diff.oldLines.size() ? leftLine++ : null;
                        Integer newLineNum = i < diff.newLines.size() ? rightLine++ : null;
                        appendLines(leftWrapper, rightWrapper, leftDoc, rightDoc, oldLine, newLine, modifiedStyle,
                                modifiedStyle, leftLineNumbers, rightLineNumbers, oldLineNum, newLineNum);
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

    private void appendLines(LineWrapper leftWrapper, LineWrapper rightWrapper, StyledDocument leftDoc,
            StyledDocument rightDoc,
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
        logger.error(message, e);
        statusBar.setText("Error: " + e.getMessage());
        JOptionPane.showMessageDialog(this, message + ":\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void clearHighlights() {
        if (leftDiffPane != null) leftDiffPane.getHighlighter().removeAllHighlights();
        if (rightDiffPane != null) rightDiffPane.getHighlighter().removeAllHighlights();
        highlights.clear();
        currentHighlightIndex = -1;
    }

    private void updateHighlights() {
        // 既存のハイライトをクリア
        clearHighlights();

        String searchText = searchField.getText();
        if (searchText == null || searchText.isEmpty()) {
            statusBar.setText("Ready");
            return;
        }

        try {
            addHighlightsInPane(leftDiffPane, searchText);
            addHighlightsInPane(rightDiffPane, searchText);
        } catch (PatternSyntaxException e) {
            statusBar.setText("Invalid Regex: " + e.getMessage());
            return;
        }

        if (!highlights.isEmpty()) {
            currentHighlightIndex = 0;
            navigateToCurrentHighlight(false); // just highlight the first one
        } else {
            statusBar.setText("Text not found: " + searchText);
        }
    }

    private void addHighlightsInPane(JTextPane pane, String searchText) throws PatternSyntaxException {
        try {
            Document doc = pane.getDocument();
            String content = doc.getText(0, doc.getLength());

            Pattern pattern;
            if (regexCheckBox.isSelected()) {
                int flags = 0;
                if (caseCheckBox.isSelected()) {
                    flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                }
                pattern = Pattern.compile(searchText, flags);
            } else {
                int flags = caseCheckBox.isSelected() ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
                pattern = Pattern.compile(Pattern.quote(searchText), flags);
            }

            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                HighlightInfo hi = new HighlightInfo(pane, matcher.start(), matcher.end());
                hi.tag = pane.getHighlighter().addHighlight(hi.start, hi.end, searchHighlightPainter);
                highlights.add(hi);
            }
        } catch (BadLocationException e) {
            logger.error("BadLocationException while adding highlights. This should not happen.", e);
        }
    }

    private void navigateHighlights(boolean forward) {
        if (highlights.isEmpty()) {
            return;
        }

        // 現在のハイライトを通常色に戻す
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
        if (currentHighlightIndex < 0 || currentHighlightIndex >= highlights.size()) {
            return;
        }

        HighlightInfo hi = highlights.get(currentHighlightIndex);

        // 新しい現在のハイライトを強調表示
        hi.pane.getHighlighter().removeHighlight(hi.tag);
        try {
            hi.tag = hi.pane.getHighlighter().addHighlight(hi.start, hi.end, currentHighlightPainter);
        } catch (BadLocationException e) { /* ignore */ }

        if (scroll) {
            // その位置にスクロール
            try {
                Rectangle viewRect = hi.pane.modelToView(hi.start);
                if (viewRect != null) {
                    hi.pane.scrollRectToVisible(viewRect);
                }
                hi.pane.setCaretPosition(hi.start);
            } catch (BadLocationException e) {
                // ignore
            }
        }

        statusBar.setText("Match " + (currentHighlightIndex + 1) + " of " + highlights.size());
    }

    private void exportHtml() {
        List<CommitInfo> selectedCommits = commitList.getSelectedValuesList();
        if (selectedCommits.size() != 2) {
            JOptionPane.showMessageDialog(this, "Please select exactly two commits to export.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        CommitInfo c1 = selectedCommits.get(0);
        CommitInfo c2 = selectedCommits.get(1);

        long time1 = c1.isUncommitted() ? Long.MAX_VALUE : c1.getCommit().getCommitTime();
        long time2 = c2.isUncommitted() ? Long.MAX_VALUE : c2.getCommit().getCommitTime();

        CommitInfo first = (time1 < time2) ? c1 : c2;
        CommitInfo second = (time1 < time2) ? c2 : c1;

        try {
            String repoPath = repoPathField.getText();
            String filePath = filePathField.getText();
            String oldContent = getFileContent(first, repoPath, filePath);
            String newContent = getFileContent(second, repoPath, filePath);

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save HTML Report");
            fileChooser.setSelectedFile(new File("diff_report.html"));
            int userSelection = fileChooser.showSaveDialog(this);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();
                if (!fileToSave.getName().toLowerCase().endsWith(".html")) {
                    fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".html");
                }
                generateHtmlReport(fileToSave, first, second, oldContent, newContent);
                JOptionPane.showMessageDialog(this, "HTML report saved successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            handleException("Error exporting HTML", e);
        }
    }

    private void generateHtmlReport(File file, CommitInfo oldCommit, CommitInfo newCommit, String oldText, String newText) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Diff Report</title>");
        html.append("<style>");
        html.append("body { font-family: sans-serif; margin: 20px; }");
        html.append("h2 { margin-bottom: 5px; }");
        html.append(".meta { margin-bottom: 20px; color: #555; }");
        html.append("table { width: 100%; border-collapse: collapse; font-family: monospace; font-size: 12px; table-layout: fixed; border: 1px solid #ddd; }");
        html.append("td { padding: 2px 4px; word-wrap: break-word; white-space: pre-wrap; vertical-align: top; }");
        html.append(".line-num { width: 40px; text-align: right; color: #999; background-color: #f5f5f5; border-right: 1px solid #ddd; user-select: none; }");
        html.append(".content { width: 50%; }");
        html.append(".left-content { border-right: 1px solid #ddd; }");
        html.append(".add { background-color: #e6ffec; }");
        html.append(".delete { background-color: #ffebe9; }");
        html.append(".modified { background-color: #e6e6ff; }");
        html.append("</style></head><body>");

        html.append("<h2>Diff Report</h2>");
        html.append("<div class='meta'>");
        html.append("<div><strong>File:</strong> ").append(escapeHtml(filePathField.getText())).append("</div>");
        html.append("<div><strong>Left (Old):</strong> ").append(escapeHtml(oldCommit.toString())).append("</div>");
        html.append("<div><strong>Right (New):</strong> ").append(escapeHtml(newCommit.toString())).append("</div>");
        html.append("</div>");

        html.append("<table>");

        List<String> oldLines = oldText.isEmpty() ? Collections.emptyList() : Arrays.asList(oldText.split("\\r?\\n"));
        List<String> newLines = newText.isEmpty() ? Collections.emptyList() : Arrays.asList(newText.split("\\r?\\n"));
        List<DiffUtils.Diff> diffs = DiffUtils.diff(oldLines, newLines);

        int leftLineNum = 1;
        int rightLineNum = 1;

        for (DiffUtils.Diff diff : diffs) {
            switch (diff.type) {
                case EQUAL:
                    for (String line : diff.lines) {
                        appendHtmlRow(html, leftLineNum++, rightLineNum++, line, line, "");
                    }
                    break;
                case DELETE:
                    for (String line : diff.lines) {
                        appendHtmlRow(html, leftLineNum++, null, line, "", "delete");
                    }
                    break;
                case INSERT:
                    for (String line : diff.lines) {
                        appendHtmlRow(html, null, rightLineNum++, "", line, "add");
                    }
                    break;
                case CHANGE:
                    int max = Math.max(diff.oldLines.size(), diff.newLines.size());
                    for (int i = 0; i < max; i++) {
                        String oldL = i < diff.oldLines.size() ? diff.oldLines.get(i) : "";
                        String newL = i < diff.newLines.size() ? diff.newLines.get(i) : "";
                        Integer lNum = i < diff.oldLines.size() ? leftLineNum++ : null;
                        Integer rNum = i < diff.newLines.size() ? rightLineNum++ : null;
                        appendHtmlRow(html, lNum, rNum, oldL, newL, "modified");
                    }
                    break;
            }
        }

        html.append("</table></body></html>");

        try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
            out.print(html.toString());
        }
    }

    private void appendHtmlRow(StringBuilder html, Integer leftNum, Integer rightNum, String leftContent, String rightContent, String cssClass) {
        html.append("<tr class='").append(cssClass).append("'>");
        html.append("<td class='line-num'>").append(leftNum != null ? leftNum : "").append("</td>");
        html.append("<td class='content left-content'>").append(escapeHtml(leftContent)).append("</td>");
        html.append("<td class='line-num'>").append(rightNum != null ? rightNum : "").append("</td>");
        html.append("<td class='content'>").append(escapeHtml(rightContent)).append("</td>");
        html.append("</tr>");
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    private void exportPatch() {
        List<CommitInfo> selectedCommits = commitList.getSelectedValuesList();
        if (selectedCommits.size() != 2) {
            JOptionPane.showMessageDialog(this, "Please select exactly two commits to export.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        CommitInfo c1 = selectedCommits.get(0);
        CommitInfo c2 = selectedCommits.get(1);

        long time1 = c1.isUncommitted() ? Long.MAX_VALUE : c1.getCommit().getCommitTime();
        long time2 = c2.isUncommitted() ? Long.MAX_VALUE : c2.getCommit().getCommitTime();

        CommitInfo first = (time1 < time2) ? c1 : c2;
        CommitInfo second = (time1 < time2) ? c2 : c1;

        try {
            String patchContent = generatePatch(first, second);

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save Patch File");
            fileChooser.setSelectedFile(new File("changes.patch"));
            int userSelection = fileChooser.showSaveDialog(this);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();
                if (!fileToSave.getName().toLowerCase().endsWith(".patch") && !fileToSave.getName().toLowerCase().endsWith(".diff")) {
                    fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".patch");
                }
                try (PrintWriter out = new PrintWriter(fileToSave, "UTF-8")) {
                    out.print(patchContent);
                }
                JOptionPane.showMessageDialog(this, "Patch file saved successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            handleException("Error exporting patch", e);
        }
    }

    private String generatePatch(CommitInfo oldCommit, CommitInfo newCommit) throws IOException {
        AbstractTreeIterator oldTree = prepareTreeParser(oldCommit);
        AbstractTreeIterator newTree = prepareTreeParser(newCommit);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DiffFormatter formatter = new DiffFormatter(out)) {
            formatter.setRepository(repository);
            formatter.setPathFilter(PathFilter.create(filePathField.getText()));
            formatter.format(oldTree, newTree);
        }
        return out.toString("UTF-8");
    }

    private AbstractTreeIterator prepareTreeParser(CommitInfo info) throws IOException {
        if (info.isUncommitted()) {
            return new FileTreeIterator(repository);
        } else {
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(info.getCommit().getId());
                CanonicalTreeParser parser = new CanonicalTreeParser();
                try (ObjectReader reader = repository.newObjectReader()) {
                    parser.reset(reader, commit.getTree());
                }
                return parser;
            }
        }
    }

    // --- mainメソッド ---
    public static void main(String[] args) {
        // 日付を含むログファイル名を生成
        String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());

        // ユーザーのホームディレクトリを取得してログファイルのパスを設定
        String userHome = System.getProperty("user.home");
        File logFile = new File(userHome, "gitfilehistory_" + dateStr + ".log");

        // ログ出力をファイルに変更し、コンソールへの出力を抑制
        System.setProperty("org.slf4j.simpleLogger.logFile", logFile.getAbsolutePath());

        // デフォルトのログレベルをWARNに設定。--debugフラグがあればDEBUGにする。
        String logLevel = "WARN";
        for (String arg : args) {
            if ("--debug".equalsIgnoreCase(arg) || "--verbose".equalsIgnoreCase(arg)) {
                logLevel = "DEBUG";
                break;
            }
        }
        // set log level for logback configuration (logback.xml reads ${LOG_LEVEL}).
        System.setProperty("LOG_LEVEL", logLevel);
        // `logback.xml` must be placed on the classpath (e.g. src/main/resources) so
        // logback can initialise. the file-only appender there prevents any output
        // to stdout/stderr.  previous version attempted to configure the simple
        // logger, which isn't on the classpath and would not affect logback anyway.

        logger = LoggerFactory.getLogger(GitDiffViewer.class);

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                logger.error("Failed to set System Look and Feel.", e);
            }
            new GitDiffViewer().setVisible(true);
        });
    }

}
