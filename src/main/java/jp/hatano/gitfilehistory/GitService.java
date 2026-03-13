/*
 * Copyright (c) 2026 Manami Hatano
 *
 * Licensed under the MIT License. See LICENSE.md in project root for details.
 */
package jp.hatano.gitfilehistory;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Service class for handling Git operations using JGit.
 */
public class GitService implements AutoCloseable {
    private Repository repository;
    private Git git;

    public GitService(File repoPath) throws IOException {
        File gitDir = new File(repoPath, ".git");
        if (!gitDir.exists()) {
            throw new IOException("'.git' directory not found at " + gitDir.getAbsolutePath());
        }
        this.repository = new FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .findGitDir()
                .build();
        this.git = new Git(repository);
    }

    public List<CommitInfo> loadCommitsForFile(String filePath, String encodingName) throws Exception {
        Map<ObjectId, List<String>> commitToBranchesMap = new HashMap<>();
        List<Ref> branches = git.branchList().call();
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

        LogCommand logCmd = git.log().addPath(filePath);
        for (Ref branch : branches) {
            logCmd.add(branch.getObjectId());
        }
        Iterable<RevCommit> logs = logCmd.call();

        List<CommitInfo> commits = new ArrayList<>();
        for (RevCommit rev : logs) {
            List<String> branchNames = commitToBranchesMap.getOrDefault(rev.getId(), Collections.emptyList());
            commits.add(new CommitInfo(rev, branchNames));
        }

        commits.sort((c1, c2) -> Integer.compare(c2.getCommit().getCommitTime(), c1.getCommit().getCommitTime()));

        if (!commits.isEmpty()) {
            String latestCommitContent = getFileContentFromRevision(commits.get(0).getCommit().getId(), filePath, encodingName);
            File localFile = new File(repository.getWorkTree(), filePath);
            if (localFile.exists()) {
                Charset charset = Charset.forName(encodingName != null ? encodingName : "UTF-8");
                String localContent = new String(Files.readAllBytes(localFile.toPath()), charset);
                if (!localContent.equals(latestCommitContent)) {
                    String nowStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                    commits.add(0, new CommitInfo("Uncommitted Changes", "Local Workspace", nowStr));
                }
            }
        }
        return commits;
    }

    public String getFileContent(CommitInfo info, String filePath, String encodingName) throws IOException {
        if (info.isUncommitted()) {
            File localFile = new File(repository.getWorkTree(), filePath);
            if (localFile.exists()) {
                Charset charset = Charset.forName(encodingName != null ? encodingName : "UTF-8");
                return new String(Files.readAllBytes(localFile.toPath()), charset);
            }
            return "";
        }
        return getFileContentFromRevision(info.getCommit().getId(), filePath, encodingName);
    }

    private String getFileContentFromRevision(ObjectId commitId, String filePath, String encodingName) throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();
            try (org.eclipse.jgit.treewalk.TreeWalk treeWalk = org.eclipse.jgit.treewalk.TreeWalk.forPath(repository, filePath, tree)) {
                if (treeWalk != null) {
                    ObjectId objectId = treeWalk.getObjectId(0);
                    byte[] bytes = repository.open(objectId).getBytes();
                    Charset charset = Charset.forName(encodingName != null ? encodingName : "UTF-8");
                    return new String(bytes, charset);
                }
            }
        }
        return "";
    }

    public String generatePatch(CommitInfo oldCommit, CommitInfo newCommit, String filePath) throws IOException {
        AbstractTreeIterator oldTree = prepareTreeParser(oldCommit);
        AbstractTreeIterator newTree = prepareTreeParser(newCommit);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DiffFormatter formatter = new DiffFormatter(out)) {
            formatter.setRepository(repository);
            formatter.setPathFilter(PathFilter.create(filePath));
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

    @Override
    public void close() {
        if (git != null) {
            git.close();
        }
        if (repository != null) {
            repository.close();
        }
    }
}
