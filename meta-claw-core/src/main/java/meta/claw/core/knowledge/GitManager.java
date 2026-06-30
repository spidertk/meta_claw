package meta.claw.core.knowledge;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.knowledge.model.GitCommitInfo;
import meta.claw.core.knowledge.model.GitFileHistory;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Component
public class GitManager {

    private Path repoPath;

    public void init(Path repoPath) {
        this.repoPath = repoPath;
        ensureGitRepo();
    }

    private void ensureGitRepo() {
        Path gitDir = repoPath.resolve(".git");
        if (!Files.exists(gitDir)) {
            log.info("Initializing git repository at {}", repoPath);
            try (Git git = Git.init().setDirectory(repoPath.toFile()).call()) {
                log.info("Git repository initialized");
            } catch (GitAPIException e) {
                log.error("Failed to init git repo: {}", e.getMessage());
            }
        }
    }

    private Repository openRepository() throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.findGitDir(repoPath.toFile());
        return builder.build();
    }

    public String commitKnowledge(Path filePath, String message) {
        return commitKnowledge(filePath, message, null);
    }

    public String commitKnowledge(Path filePath, String message, String author) {
        try (Repository repo = openRepository();
             Git git = new Git(repo)) {

            String relativePath = repoPath.relativize(filePath).toString();
            git.add().addFilepattern(relativePath).call();

            org.eclipse.jgit.api.CommitCommand commitCmd = git.commit()
                    .setMessage(message);
            if (author != null) {
                commitCmd.setAuthor(author, author);
            }
            RevCommit commit = commitCmd.call();

            String commitHash = commit.getId().getName();
            log.info("Committed {} with hash {}", relativePath, commitHash.substring(0, 8));
            return commitHash;
        } catch (IOException | GitAPIException e) {
            log.error("Failed to commit knowledge: {}", e.getMessage());
            return "";
        }
    }

    public GitFileHistory getFileHistory(Path filePath, int maxCommits) {
        try (Repository repo = openRepository()) {
            String currentContent = "";
            if (Files.exists(filePath)) {
                currentContent = Files.readString(filePath);
            }

            String currentCommit = "";
            ObjectId headId = repo.resolve("HEAD");
            if (headId != null) {
                currentCommit = headId.getName();
            }

            List<GitCommitInfo> recentCommits = getRecentCommits(repo, filePath, maxCommits);

            return GitFileHistory.builder()
                    .currentContent(currentContent)
                    .currentCommit(currentCommit)
                    .recentCommits(recentCommits)
                    .build();
        } catch (IOException e) {
            log.error("Failed to get file history: {}", e.getMessage());
            return GitFileHistory.builder()
                    .currentContent("")
                    .currentCommit("")
                    .recentCommits(Collections.emptyList())
                    .build();
        }
    }

    private List<GitCommitInfo> getRecentCommits(Repository repo, Path filePath, int maxCommits) {
        try (Git git = new Git(repo)) {
            String relativePath = repoPath.relativize(filePath).toString();

            Iterable<RevCommit> logIterable = git.log()
                    .addPath(relativePath)
                    .setMaxCount(maxCommits)
                    .call();

            return StreamSupport.stream(logIterable.spliterator(), false)
                    .map(commit -> GitCommitInfo.builder()
                            .hash(commit.getId().getName())
                            .author(commit.getAuthorIdent().getName())
                            .date(Instant.ofEpochSecond(commit.getCommitTime()))
                            .message(commit.getShortMessage())
                            .filesChanged(Collections.emptyList())
                            .build())
                    .collect(Collectors.toList());
        } catch (GitAPIException e) {
            log.warn("Failed to get recent commits: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Path> grepFiles(List<String> keywords, Path searchPath) {
        return grepFiles(keywords, searchPath, "*.md");
    }

    public List<Path> grepFiles(List<String> keywords, Path searchPath, String glob) {
        if (keywords == null || keywords.isEmpty()) {
            return Collections.emptyList();
        }

        Path targetPath = searchPath != null ? searchPath : repoPath;
        String filePattern = glob != null ? glob : "*.md";
        List<Path> results = new ArrayList<>();

        try {
            if (!Files.exists(targetPath)) {
                return results;
            }
            try (var stream = Files.walk(targetPath)) {
                stream.filter(p -> p.getFileSystem().getPathMatcher("glob:" + filePattern).matches(p.getFileName()))
                        .forEach(filePath -> {
                            try {
                                String content = Files.readString(filePath).toLowerCase();
                                for (String kw : keywords) {
                                    if (content.contains(kw.toLowerCase())) {
                                        results.add(filePath);
                                        break;
                                    }
                                }
                            } catch (IOException e) {
                                log.debug("Failed to read {}: {}", filePath, e.getMessage());
                            }
                        });
            }
        } catch (IOException e) {
            log.warn("Failed to grep files: {}", e.getMessage());
        }

        return results;
    }

    public boolean createBranch(String branchName, String base) {
        try (Repository repo = openRepository();
             Git git = new Git(repo)) {
            git.checkout()
                    .setCreateBranch(true)
                    .setName(branchName)
                    .setStartPoint(base)
                    .call();
            log.info("Created branch: {}", branchName);
            return true;
        } catch (IOException | GitAPIException e) {
            log.error("Failed to create branch: {}", e.getMessage());
            return false;
        }
    }

    public boolean checkoutBranch(String branchName) {
        try (Repository repo = openRepository();
             Git git = new Git(repo)) {
            git.checkout()
                    .setName(branchName)
                    .call();
            return true;
        } catch (IOException | GitAPIException e) {
            log.error("Failed to checkout branch: {}", e.getMessage());
            return false;
        }
    }

    public String getCurrentBranch() {
        try (Repository repo = openRepository()) {
            return repo.getBranch();
        } catch (IOException e) {
            return "";
        }
    }

    public List<String> listBranches() {
        try (Repository repo = openRepository();
             Git git = new Git(repo)) {
            return git.branchList().call().stream()
                    .map(ref -> ref.getName().replace("refs/heads/", ""))
                    .collect(Collectors.toList());
        } catch (IOException | GitAPIException e) {
            log.error("Failed to list branches: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public boolean mergeBranch(String branchName, String message) {
        try (Repository repo = openRepository();
             Git git = new Git(repo)) {
            git.merge()
                    .include(repo.resolve(branchName))
                    .setMessage(message)
                    .call();
            return true;
        } catch (IOException | GitAPIException e) {
            log.error("Failed to merge branch: {}", e.getMessage());
            return false;
        }
    }
}