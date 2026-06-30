package meta.claw.tool.knowledge.model;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
@Builder
public class GitFileHistory {
    private String currentContent;
    private String currentCommit;
    @Builder.Default
    private List<GitCommitInfo> recentCommits = Collections.emptyList();
}