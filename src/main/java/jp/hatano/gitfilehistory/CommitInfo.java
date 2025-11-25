package jp.hatano.gitfilehistory;

import org.eclipse.jgit.revwalk.RevCommit;

import java.text.SimpleDateFormat;

/**
 * Holds information about a single Git commit.
 */
public class CommitInfo {
    private final RevCommit commit;
    private final String shortHash;
    final String author;
    final String date;
    final String message;

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