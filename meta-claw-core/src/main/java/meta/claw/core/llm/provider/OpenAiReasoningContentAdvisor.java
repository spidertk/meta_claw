package meta.claw.core.llm.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 参考 Spring AI Alibaba Playground 的 ReasoningContentAdvisor 实现。
 * 在请求发送前扫描 Prompt 中的 assistant 消息，将其 metadata 中的 reasoningContent
 * 写入 {@link OpenAiReasoningContentContext}，供 {@link OpenAiReasoningContentModule}
 * 在序列化 OpenAI 兼容请求时回填。
 */
@Slf4j
public class OpenAiReasoningContentAdvisor implements BaseAdvisor {

    private final int order;

    public OpenAiReasoningContentAdvisor(Integer order) {
        this.order = order != null ? order : 0;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 每次新的 LLM 调用前清空旧上下文，避免历史数据污染
        OpenAiReasoningContentContext.clear();

        List<Message> messages = chatClientRequest.prompt().getInstructions();
        for (Message message : messages) {
            if (message instanceof AssistantMessage assistantMessage) {
                String reasoningContent = extractReasoningContent(assistantMessage);
                OpenAiReasoningContentContext.push(reasoningContent);
                if (log.isDebugEnabled() && !reasoningContent.isEmpty()) {
                    log.debug("[OpenAiReasoningContentAdvisor] Pushed reasoning_content for assistant message: {}",
                            reasoningContent.substring(0, Math.min(50, reasoningContent.length())) + "...");
                }
            }
        }

        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        // 请求-响应周期结束，清理线程上下文
        OpenAiReasoningContentContext.remove();
        return chatClientResponse;
    }

    @SuppressWarnings("unchecked")
    private static String extractReasoningContent(AssistantMessage assistantMessage) {
        Object reasoning = null;
        if (assistantMessage.getMetadata() != null) {
            reasoning = assistantMessage.getMetadata().get("reasoningContent");
        }
        if (reasoning == null && assistantMessage.getMetadata() != null) {
            reasoning = assistantMessage.getMetadata().get("reasoning_content");
        }
        return reasoning instanceof String s ? s : "";
    }
}
