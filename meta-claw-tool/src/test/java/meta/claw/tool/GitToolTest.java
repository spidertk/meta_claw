package meta.claw.tool;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitToolTest {

    private final GitTool tool = new GitTool();

    @Test
    void statusAndLogOnFreshRepo(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Path file = tempDir.resolve("hello.txt");
            Files.writeString(file, "hello");
            git.add().addFilepattern("hello.txt").call();
            git.commit()
                    .setMessage("initial commit")
                    .setAuthor("Test User", "test@example.com")
                    .call();

            String status = tool.gitStatus(tempDir.toString());
            assertEquals("working tree clean", status, "expected clean working tree but got: " + status);

            String log = tool.gitLog(tempDir.toString(), 5);
            assertTrue(log.contains("initial commit"), "expected commit message in log but got: " + log);
            assertTrue(log.contains("Test User"), "expected author in log but got: " + log);
        }
    }

    @Test
    void diffShowsWorkingTreeChanges(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Path file = tempDir.resolve("hello.txt");
            Files.writeString(file, "hello");
            git.add().addFilepattern("hello.txt").call();
            git.commit()
                    .setMessage("initial commit")
                    .setAuthor("Test User", "test@example.com")
                    .call();

            Files.writeString(file, "hello world");
            String diff = tool.gitDiff(tempDir.toString(), "HEAD", null);
            assertTrue(diff.contains("MODIFY") && diff.contains("hello.txt"),
                    "expected diff output but got: " + diff);
        }
    }

    @Test
    void logLimitsNumberOfCommits(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            for (int i = 0; i < 5; i++) {
                Path file = tempDir.resolve("file" + i + ".txt");
                Files.writeString(file, "content" + i);
                git.add().addFilepattern("file" + i + ".txt").call();
                git.commit()
                        .setMessage("commit " + i)
                        .setAuthor("Test User", "test@example.com")
                        .call();
            }

            String log = tool.gitLog(tempDir.toString(), 2);
            long lines = log.lines().count();
            assertEquals(2L, lines, "expected 2 log lines but got: " + log);
        }
    }
}
