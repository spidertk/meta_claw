package meta.claw.core.knowledge.extract;

import meta.claw.core.llm.MediaPart;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiLlmClient;
import meta.claw.core.llm.SpiMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class VisionDescriber {

    private final SpiLlmClient llmClient;

    @Autowired
    public VisionDescriber(SpiLlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public String describe(Path imagePath, String mimeType) {
        if (llmClient == null) {
            return "[Image: " + imagePath.getFileName() + "]";
        }

        MediaPart part = MediaPart.builder()
                .type("image_url")
                .mimeType(mimeType)
                .url(imagePath.toUri().toString())
                .build();

        SpiChatRequest request = SpiChatRequest.builder()
                .messages(List.of(SpiMessage.user(
                        "请用一段简洁的中文描述这张图片的内容，提取其中的文字和关键信息。", List.of(part))))
                .build();

        try {
            SpiChatResponse response = llmClient.chat(request);
            return response.content();
        } catch (Exception e) {
            return "[Failed to describe image: " + imagePath.getFileName() + "]";
        }
    }
}
