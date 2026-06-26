package meta.claw.core.llm.advisor;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.llm.provider.OpenAiReasoningContentContext;
import meta.claw.core.llm.provider.OpenAiReasoningContentModule;
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
        if (log.isDebugEnabled()) {
            log.debug("[OpenAiReasoningContentAdvisor] Processing {} message(s) for reasoning_content passthrough", messages.size());
        }
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message instanceof AssistantMessage assistantMessage) {
                String reasoningContent = extractReasoningContent(assistantMessage);
                OpenAiReasoningContentContext.push(reasoningContent);
                if (log.isDebugEnabled()) {
                    log.debug("[OpenAiReasoningContentAdvisor] Message #{} assistant hasToolCalls={} reasoningContent={}",
                            i,
                            assistantMessage.hasToolCalls(),
                            reasoningContent.isEmpty() ? "<empty>" : reasoningContent.substring(0, Math.min(80, reasoningContent.length())) + "...");
                }
            }
        }

        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        // 请求-响应周期结束，清理线程上下文
        if (log.isDebugEnabled()) {
            log.debug("[OpenAiReasoningContentAdvisor] Response received, removing reasoning_content context");
        }
        OpenAiReasoningContentContext.remove();
        return chatClientResponse;
    }

    @SuppressWarnings("unchecked")
    private static String extractReasoningContent(AssistantMessage assistantMessage) {
        Object reasoning = null;
        if (assistantMessage.getMetadata() != null) {
            reasoning = assistantMessage.getMetadata().get("reasoningContent");
            if (log.isDebugEnabled()) {
                log.debug("[OpenAiReasoningContentAdvisor] AssistantMessage.metadata keys={} reasoningContent={}",
                        assistantMessage.getMetadata().keySet(),
                        reasoning instanceof String s ? s.substring(0, Math.min(80, s.length())) + "..." : reasoning);
            }
        }
        if (reasoning == null && assistantMessage.getMetadata() != null) {
            reasoning = assistantMessage.getMetadata().get("reasoning_content");
        }
        return reasoning instanceof String s ? s : "";
    }
}
