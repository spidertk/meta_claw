package meta.claw.core.knowledge.extract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.llm.MediaPart;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiLlmClient;
import meta.claw.core.llm.SpiMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 视觉理解服务：一次 LLM 调用同时产出图片描述与检索关键词，
 * 避免「描述一次、关键词再提取一次」的双倍调用。
 */
@Slf4j
@Component
public class VisionDescriber {

    private static final int MAX_IMAGES_PER_CALL = 5;

    private final SpiLlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public VisionDescriber(SpiLlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 分析单张图片，返回描述 + 关键词。
     */
    public VisionInsight analyze(Path imagePath, String mimeType, String vesselId) {
        return analyze(List.of(imagePath), mimeType, vesselId);
    }

    /**
     * 一次调用分析多张图片（如 PDF 页图），返回合并的描述 + 关键词。
     * 图片数量超过 {@value #MAX_IMAGES_PER_CALL} 时截断。
     */
    public VisionInsight analyze(List<Path> imagePaths, String mimeType, String vesselId) {
        if (llmClient == null || imagePaths == null || imagePaths.isEmpty()) {
            return VisionInsight.builder()
                    .description("[Image: " + (imagePaths != null && !imagePaths.isEmpty()
                            ? imagePaths.get(0).getFileName() : "unknown") + "]")
                    .build();
        }

        List<MediaPart> parts = new ArrayList<>();
        imagePaths.stream().limit(MAX_IMAGES_PER_CALL).forEach(p -> parts.add(MediaPart.builder()
                .type("image_url")
                .mimeType(mimeType)
                .url(p.toUri().toString())
                .build()));

        String prompt = """
                请分析这张/这些图片，完成两个任务：
                1. 用一段简洁的中文描述图片内容，提取其中的文字和关键信息（多张图片时合并描述）。
                2. 提取5-10个关键词，用于在知识库中搜索相关知识。
                请以JSON格式返回：{"description": "...", "keywords": ["关键词1", "关键词2", ...]}
                请只返回JSON，不要有其他内容。""";

        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId(vesselId)
                .messages(List.of(SpiMessage.user(prompt, parts)))
                .build();

        try {
            SpiChatResponse response = llmClient.chat(request);
            return parseInsight(response.content(), imagePaths.get(0));
        } catch (Exception e) {
            log.warn("Vision analysis failed: {}", e.getMessage());
            return VisionInsight.builder()
                    .description("[Failed to describe image: " + imagePaths.get(0).getFileName() + "]")
                    .build();
        }
    }

    /**
     * 兼容旧接口：仅返回描述文本。
     */
    public String describe(Path imagePath, String mimeType, String vesselId) {
        return analyze(imagePath, mimeType, vesselId).getDescription();
    }

    private VisionInsight parseInsight(String content, Path firstImage) {
        if (content == null || content.isBlank()) {
            return VisionInsight.builder()
                    .description("[Failed to describe image: " + firstImage.getFileName() + "]")
                    .build();
        }
        String jsonStr = content;
        if (jsonStr.contains("```json")) {
            jsonStr = jsonStr.split("```json")[1].split("```")[0].strip();
        } else if (jsonStr.contains("```")) {
            jsonStr = jsonStr.split("```")[1].split("```")[0].strip();
        }
        try {
            Map<String, Object> data = objectMapper.readValue(jsonStr, new TypeReference<>() {});
            List<String> keywords = new ArrayList<>();
            Object kw = data.get("keywords");
            if (kw instanceof List<?> list) {
                for (Object item : list) {
                    keywords.add(String.valueOf(item));
                }
            }
            return VisionInsight.builder()
                    .description(String.valueOf(data.getOrDefault("description", content)))
                    .keywords(keywords)
                    .build();
        } catch (Exception e) {
            // 模型未按 JSON 返回时，整体作为描述，关键词留空（由后续环节兜底）
            log.debug("Vision insight not JSON, using raw content as description: {}", e.getMessage());
            return VisionInsight.builder()
                    .description(content)
                    .keywords(Collections.emptyList())
                    .build();
        }
    }
}
