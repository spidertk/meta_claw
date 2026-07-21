package meta.claw.core.knowledge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 待审知识提案：分析完成后、人审确认前持久化到
 * knowledge/.pending/{proposalId}.json，确认后才真正落库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeProposal {
    private String proposalId;
    private String vesselId;
    private String assetId;
    private String sha256;
    private String mediaType;
    private String markdownBody;
    private String context;
    /** 为 null 表示 dryRun 只做了内容提取，分析（含矛盾自检）延迟到 approve 时补跑 */
    private AnalysisResult analysis;
    @Builder.Default
    private List<String> relatedEntryIds = new ArrayList<>();
    private String createdAt;
}
