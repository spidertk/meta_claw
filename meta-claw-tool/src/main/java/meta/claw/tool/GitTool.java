package meta.claw.tool;

import meta.claw.core.tool.annotation.ToolService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Git 仓库操作工具。
 * <p>
 * 基于 Eclipse JGit 实现，支持 status、log、diff 等常用查询，不依赖系统 git 可执行文件。
 */
@ToolService
public class GitTool {

    private static final int DEFAULT_LOG_LIMIT = 10;

    @Tool(description = "Show the working tree status of a Git repository: modified, added, removed and untracked files.")
    public String gitStatus(
            @ToolParam(description = "Path to the Git repository (relative or absolute)") String repositoryPath) {
        if (repositoryPath == null || repositoryPath.isBlank()) {
            return "Error: repository path is empty";
        }
        try (Git git = Git.open(resolveDirectory(repositoryPath))) {
            org.eclipse.jgit.api.Status status = git.status().call();
            StringBuilder sb = new StringBuilder();
            appendSet(sb, "Modified", status.getModified());
            appendSet(sb, "Added", status.getAdded());
            appendSet(sb, "Removed", status.getRemoved());
            appendSet(sb, "Untracked", status.getUntracked());
            appendSet(sb, "Conflicting", status.getConflicting());
            if (sb.isEmpty()) {
                return "working tree clean";
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Show recent commit history of a Git repository.")
    public String gitLog(
            @ToolParam(description = "Path to the Git repository") String repositoryPath,
            @ToolParam(description = "Number of commits to show (default 10)", required = false) Integer limit) {
        if (repositoryPath == null || repositoryPath.isBlank()) {
            return "Error: repository path is empty";
        }
        int max = limit != null && limit > 0 ? limit : DEFAULT_LOG_LIMIT;
        try (Git git = Git.open(resolveDirectory(repositoryPath))) {
            StringBuilder sb = new StringBuilder();
            for (RevCommit commit : git.log().setMaxCount(max).call()) {
                sb.append(commit.getId().abbreviate(7).name())
                        .append(" ")
                        .append(commit.getAuthorIdent().getName())
                        .append(" ")
                        .append(commit.getShortMessage())
                        .append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Show differences between two refs or between a ref and the working tree. "
            + "Returns a name-status list of changed files (safe for uncommitted working tree changes).")
    public String gitDiff(
            @ToolParam(description = "Path to the Git repository") String repositoryPath,
            @ToolParam(description = "Old ref, e.g. HEAD~1") String oldRef,
            @ToolParam(description = "New ref, e.g. HEAD. Omit to compare against working tree.", required = false) String newRef) {
        if (repositoryPath == null || repositoryPath.isBlank()) {
            return "Error: repository path is empty";
        }
        if (oldRef == null || oldRef.isBlank()) {
            return "Error: oldRef is required";
        }
        try (Repository repo = openRepository(repositoryPath);
             Git git = new Git(repo);
             ObjectReader reader = repo.newObjectReader()) {

            AbstractTreeIterator oldTree = resolveTreeIterator(reader, repo, oldRef);
            AbstractTreeIterator newTree = (newRef == null || newRef.isBlank())
                    ? new FileTreeIterator(repo)
                    : resolveTreeIterator(reader, repo, newRef);
            List<DiffEntry> diffs = git.diff()
                    .setOldTree(oldTree)
                    .setNewTree(newTree)
                    .call();
            if (diffs.isEmpty()) {
                return "no differences";
            }
            StringBuilder sb = new StringBuilder();
            for (DiffEntry diff : diffs) {
                sb.append(diff.getChangeType())
                        .append("\t")
                        .append(diff.getNewPath())
                        .append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private File resolveDirectory(String repositoryPath) {
        return Path.of(repositoryPath).toAbsolutePath().normalize().toFile();
    }

    private Repository openRepository(String repositoryPath) throws IOException {
        File dir = resolveDirectory(repositoryPath);
        if (!dir.exists()) {
            throw new IOException("repository path does not exist: " + dir);
        }
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.findGitDir(dir);
        if (builder.getGitDir() == null) {
            throw new IOException("no .git directory found under " + dir);
        }
        return builder.build();
    }

    private AbstractTreeIterator resolveTreeIterator(ObjectReader reader, Repository repo, String ref) throws IOException {
        RevCommit commit = repo.parseCommit(repo.resolve(ref));
        RevTree tree = commit.getTree();
        CanonicalTreeParser parser = new CanonicalTreeParser();
        parser.reset(reader, tree.getId());
        return parser;
    }

    private void appendSet(StringBuilder sb, String label, Set<String> set) {
        if (set == null || set.isEmpty()) {
            return;
        }
        sb.append(label).append(":\n");
        for (String s : set) {
            sb.append("  ").append(s).append("\n");
        }
    }
}
