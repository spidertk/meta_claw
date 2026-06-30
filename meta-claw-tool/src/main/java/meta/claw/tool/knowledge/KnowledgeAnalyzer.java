package meta.claw.tool.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.llm.MediaPart;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiLlmClient;
import meta.claw.core.llm.SpiMessage;
import meta.claw.tool.knowledge.model.AnalysisResult;
import meta.claw.tool.knowledge.model.ContradictionInfo;
import meta.claw.tool.knowledge.model.KnowledgeEntry;
import meta.claw.tool.knowledge.multimodal.ModelCapability;
import meta.claw.tool.knowledge.source.AssetRef;
import meta.claw.tool.knowledge.source.ExtractedDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class KnowledgeAnalyzer {

    private final SpiLlmClient llmClient;
    private final ModelCapability modelCapability;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public KnowledgeAnalyzer(SpiLlmClient llmClient, ModelCapability modelCapability) {
        this.llmClient = llmClient;
        this.modelCapability = modelCapability;
    }

    public AnalysisResult analyze(ExtractedDocument doc,
                                  List<KnowledgeEntry> relatedEntries,
                                  String context) {
        if (llmClient == null) {
            log.warn("No LLM client available, returning default analysis");
            return defaultAnalysis();
        }

        boolean useMultimodal = modelCapability.supportsMultimodal()
                && hasVisualAssets(doc)
                && modelCapability.supportsMediaType(doc.getMediaType());

        if (useMultimodal) {
            return analyzeWithMultimodal(doc, relatedEntries, context);
        }

        return analyzeTextFallback(doc, relatedEntries, context);
    }

    public AnalysisResult analyze(String newContent, List<KnowledgeEntry> relatedEntries, String context) {
        if (llmClient == null) {
            log.warn("No LLM client available, returning default analysis");
            return defaultAnalysis();
        }

        String prompt = buildAnalysisPrompt(newContent, relatedEntries, context);

        try {
            SpiChatRequest request = SpiChatRequest.builder()
                    .messages(List.of(SpiMessage.user(prompt)))
                    .build();

            SpiChatResponse response = llmClient.chat(request);
            return parseAnalysisResponse(response.content(), newContent);
        } catch (Exception e) {
            log.error("LLM analysis failed: {}", e.getMessage());
            return defaultAnalysis();
        }
    }

    private boolean hasVisualAssets(ExtractedDocument doc) {
        if (doc == null || doc.getEmbeddedAssets() == null || doc.getEmbeddedAssets().isEmpty()) {
            return false;
        }
        return doc.getEmbeddedAssets().stream()
                .anyMatch(a -> a.getMediaType() != null && a.getMediaType().startsWith("image/"));
    }

    private AnalysisResult analyzeWithMultimodal(ExtractedDocument doc,
                                                 List<KnowledgeEntry> relatedEntries,
                                                 String context) {
        List<MediaPart> mediaParts = doc.getEmbeddedAssets().stream()
                .filter(a -> a.getMediaType() != null && a.getMediaType().startsWith("image/"))
                .filter(a -> a.getOriginalPath() != null)
                .map(a -> MediaPart.builder()
                        .type("image_url")
                        .mimeType(a.getMediaType())
                        .url(a.getOriginalPath().toUri().toString())
                        .build())
                .limit(5)
                .collect(Collectors.toList());

        String prompt = buildAnalysisPrompt(doc.getMarkdownBody(), relatedEntries, context);
        SpiChatRequest request = SpiChatRequest.builder()
                .messages(List.of(SpiMessage.user(prompt, mediaParts)))
                .build();

        try {
            SpiChatResponse response = llmClient.chat(request);
            AnalysisResult result = parseAnalysisResponse(response.content(), doc.getMarkdownBody());
            result.setMultimodalUsed(true);
            return result;
        } catch (Exception e) {
            log.error("Multimodal analysis failed, falling back to text: {}", e.getMessage());
            return analyzeTextFallback(doc, relatedEntries, context);
        }
    }

    private AnalysisResult analyzeTextFallback(ExtractedDocument doc,
                                               List<KnowledgeEntry> relatedEntries,
                                               String context) {
        String prompt = buildAnalysisPrompt(doc.getMarkdownBody(), relatedEntries, context);
        try {
            SpiChatRequest request = SpiChatRequest.builder()
                    .messages(List.of(SpiMessage.user(prompt)))
                    .build();
            SpiChatResponse response = llmClient.chat(request);
            return parseAnalysisResponse(response.content(), doc.getMarkdownBody());
        } catch (Exception e) {
            log.error("LLM text analysis failed: {}", e.getMessage());
            return defaultAnalysis();
        }
    }

    private String buildAnalysisPrompt(String newContent, List<KnowledgeEntry> relatedEntries, String context) {
        StringBuilder relatedSection = new StringBuilder();
        if (relatedEntries != null && !relatedEntries.isEmpty()) {
            relatedSection.append("\n### 当前知识库中的相关条目\n\n");
            int idx = 1;
            for (KnowledgeEntry entry : relatedEntries) {
                relatedSection.append("[").append(idx).append("] ID: ").append(entry.getId()).append("\n");
                relatedSection.append("类型: ").append(entry.getKnowledgeType().getValue()).append("\n");
                relatedSection.append("状态: ").append(entry.getStatus().getValue()).append("\n");
                if (entry.getCommitHash() != null && !entry.getCommitHash().isEmpty()) {
                    relatedSection.append("版本: ").append(entry.getCommitHash(), 0, Math.min(8, entry.getCommitHash().length())).append("\n");
                }
                relatedSection.append("\n内容:\n");
                String content = entry.getContent();
                if (content != null && content.length() > 500) {
                    relatedSection.append(content, 0, 500).append("...\n");
                } else {
                    relatedSection.append(content).append("\n");
                }
                idx++;
            }
        }

        String contextSection = (context != null && !context.isEmpty()) ? "\n### 附加上下文\n" + context + "\n" : "";

        return """
                你是一位知识库管理员。请分析专家输入的新知识，判断其类型并检查是否与现有知识矛盾。
                
                ## 分析任务
                
                ### 新知识
                
                %s
                
                %s
                %s
                
                ## 输出要求
                
                请以JSON格式返回分析结果：
                
                ```json
                {
                    "knowledge_type": "fact" | "opinion" | "unknown",
                    "is_fact": true | false,
                    "contradiction": {
                        "detected": true | false,
                        "conflicting_entry_id": "相关条目ID或空字符串",
                        "explanation": "矛盾解释",
                        "confidence": 0.0-1.0,
                        "contradiction_type": "direct" | "semantic" | "temporal" | ""
                    },
                    "confidence": 0.0-1.0,
                    "recommended_action": "add" | "replace" | "reject" | "manual_review",
                    "reasoning": "详细的推理过程",
                    "extracted_keywords": ["关键词1", "关键词2"],
                    "suggested_topics": ["主题1", "主题2"],
                    "suggested_title": "建议的标题",
                    "commit_summary": "一句话提交摘要",
                    "commit_description": "详细的提交描述"
                }
                ```
                
                ## 判断标准
                
                **知识类型：**
                - fact: 可验证的客观事实
                - opinion: 主观观点、建议、最佳实践
                
                **矛盾检测（仅针对facts）：**
                检查新知识是否与现有active facts在语义上矛盾
                - 直接矛盾：两者不能同时为真
                - 语义矛盾：虽然表述不同但意思冲突
                - 时序矛盾：同一主题在不同时期的不同理解
                
                **置信度评分：**
                - 0.9-1.0: 非常确定，可直接执行
                - 0.7-0.9: 较确定，但建议人工确认
                - 0.5-0.7: 不确定，需要人工判断
                - <0.5: 无法判断
                
                **推荐操作：**
                - add: 无矛盾，直接添加
                - replace: 发现矛盾，用新知识替换旧知识
                - reject: 新知识有问题，拒绝添加
                - manual_review: 需要人工确认
                
                请只返回JSON，不要有其他内容。""".formatted(newContent, relatedSection.toString(), contextSection);
    }

    private AnalysisResult parseAnalysisResponse(String response, String originalContent) {
        String jsonStr = response;
        if (response.contains("```json")) {
            jsonStr = response.split("```json")[1].split("```")[0].strip();
        } else if (response.contains("```")) {
            jsonStr = response.split("```")[1].split("```")[0].strip();
        }

        try {
            Map<String, Object> data = objectMapper.readValue(jsonStr, new TypeReference<>() {});

            @SuppressWarnings("unchecked")
            Map<String, Object> contraData = (Map<String, Object>) data.getOrDefault("contradiction", Collections.emptyMap());

            ContradictionInfo contradiction = ContradictionInfo.builder()
                    .detected(Boolean.TRUE.equals(contraData.get("detected")))
                    .conflictingEntryId(String.valueOf(contraData.getOrDefault("conflicting_entry_id", "")))
                    .explanation(String.valueOf(contraData.getOrDefault("explanation", "")))
                    .confidence(getDouble(contraData, "confidence"))
                    .contradictionType(String.valueOf(contraData.getOrDefault("contradiction_type", "")))
                    .build();

            @SuppressWarnings("unchecked")
            List<String> keywords = (List<String>) data.getOrDefault("extracted_keywords", Collections.emptyList());

            @SuppressWarnings("unchecked")
            List<String> topics = (List<String>) data.getOrDefault("suggested_topics", Collections.emptyList());

            return AnalysisResult.builder()
                    .knowledgeType(String.valueOf(data.getOrDefault("knowledge_type", "unknown")))
                    .isFact(Boolean.TRUE.equals(data.get("is_fact")))
                    .contradiction(contradiction)
                    .confidence(getDouble(data, "confidence"))
                    .recommendedAction(String.valueOf(data.getOrDefault("recommended_action", "manual_review")))
                    .reasoning(String.valueOf(data.getOrDefault("reasoning", "")))
                    .extractedKeywords(keywords)
                    .suggestedTopics(topics)
                    .suggestedTitle(String.valueOf(data.getOrDefault("suggested_title", "")))
                    .commitSummary(String.valueOf(data.getOrDefault("commit_summary", "")))
                    .commitDescription(String.valueOf(data.getOrDefault("commit_description", "")))
                    .rawResponse(Map.of("response", response))
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse LLM response as JSON: {}", e.getMessage());
            log.error("Response: {}", response.substring(0, Math.min(500, response.length())));
            return defaultAnalysis();
        }
    }

    private double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        return 0.0;
    }

    private AnalysisResult defaultAnalysis() {
        return AnalysisResult.builder()
                .knowledgeType("unknown")
                .isFact(false)
                .contradiction(ContradictionInfo.builder().detected(false).build())
                .confidence(0.0)
                .recommendedAction("manual_review")
                .reasoning("LLM分析失败，需要人工判断")
                .extractedKeywords(Collections.emptyList())
                .suggestedTopics(Collections.emptyList())
                .suggestedTitle("")
                .commitSummary("添加新知识")
                .commitDescription("")
                .build();
    }

    public List<String> extractKeywords(String content) {
        if (llmClient == null) {
            return Arrays.stream(content.split("\\s+"))
                    .filter(w -> w.length() > 3)
                    .limit(5)
                    .collect(Collectors.toList());
        }

        String prompt = """
                从以下知识内容中提取5-10个关键词，用于搜索相关知识。
                内容：
                %s
                请以JSON数组格式返回：["关键词1", "关键词2", ...]
                """.formatted(content.length() > 1000 ? content.substring(0, 1000) : content);

        try {
            SpiChatRequest request = SpiChatRequest.builder()
                    .messages(List.of(SpiMessage.user(prompt)))
                    .build();

            SpiChatResponse response = llmClient.chat(request);
            String jsonStr = response.content();
            if (jsonStr.contains("```")) {
                jsonStr = jsonStr.split("```")[1].split("```")[0].strip();
            }

            List<String> keywords = objectMapper.readValue(jsonStr, new TypeReference<>() {});
            return keywords.stream().limit(10).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Keyword extraction failed: {}", e.getMessage());
            return Arrays.stream(content.split("\\s+"))
                    .filter(w -> w.length() > 3)
                    .limit(5)
                    .collect(Collectors.toList());
        }
    }
}
