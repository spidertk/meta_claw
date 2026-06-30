package meta.claw.tool.knowledge.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Data
@Builder
public class GitCommitInfo {
    private String hash;
    private String author;
    private Instant date;
    private String message;
    @Builder.Default
    private List<String> filesChanged = Collections.emptyList();
}