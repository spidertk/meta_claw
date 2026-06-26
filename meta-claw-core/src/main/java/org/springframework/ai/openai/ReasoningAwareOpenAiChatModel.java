package org.springframework.ai.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 扩展 {@link OpenAiChatModel}，修复 Spring AI 1.1.8 在把带 reasoningContent 的
 * {@link AssistantMessage} 转成 {@link OpenAiApi.ChatCompletionMessage} 时
 * 硬编码 {@code reasoningContent = null} 的问题。
 * <p>
 * 通过覆盖 {@link #createRequest(Prompt, boolean)}，在父类构建完请求后，
 * 根据原始 Prompt 中 AssistantMessage 的 metadata 回填真实的 reasoning_content。
 */
@Slf4j
public class ReasoningAwareOpenAiChatModel extends OpenAiChatModel {

    private final ObjectMapper objectMapper;

    public ReasoningAwareOpenAiChatModel(OpenAiApi openAiApi, OpenAiChatOptions defaultOptions,
                                         ObjectMapper objectMapper) {
        super(openAiApi, defaultOptions, ToolCallingManager.builder().build(),
                RetryUtils.DEFAULT_RETRY_TEMPLATE, ObservationRegistry.NOOP,
                new DefaultToolExecutionEligibilityPredicate());
        this.objectMapper = objectMapper;
    }

    @Override
    OpenAiApi.ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {
        OpenAiApi.ChatCompletionRequest baseRequest = super.createRequest(prompt, stream);

        Map<String, String> reasoningByToolCallIds = buildReasoningMap(prompt);
        if (reasoningByToolCallIds.isEmpty()) {
            return baseRequest;
        }

        try {
            JsonNode node = objectMapper.valueToTree(baseRequest);
            if (!node.isObject()) {
                return baseRequest;
            }
            JsonNode messagesNode = node.get("messages");
            if (messagesNode == null || !messagesNode.isArray()) {
                return baseRequest;
            }

            boolean patched = false;
            for (JsonNode msgNode : messagesNode) {
                if (!msgNode.isObject()) {
                    continue;
                }
                ObjectNode msgObj = (ObjectNode) msgNode;
                String role = msgObj.path("role").asText("");
                JsonNode toolCallsNode = msgObj.get("tool_calls");
                boolean hasToolCalls = toolCallsNode != null && toolCallsNode.isArray() && toolCallsNode.size() > 0;
                boolean hasReasoning = msgObj.hasNonNull("reasoning_content");

                if ("assistant".equals(role) && hasToolCalls && !hasReasoning) {
                    String key = toolCallIdsKey(toolCallsNode);
                    String reasoningContent = reasoningByToolCallIds.getOrDefault(key, "");
                    msgObj.put("reasoning_content", reasoningContent);
                    patched = true;
                    if (log.isDebugEnabled()) {
                        log.debug("[ReasoningAwareOpenAiChatModel] Patched reasoning_content for tool_call message: key={} reasoning={}",
                                key,
                                reasoningContent.isEmpty() ? "<empty>" : reasoningContent.substring(0, Math.min(80, reasoningContent.length())) + "...");
                    }
                }
            }

            if (!patched) {
                return baseRequest;
            }

            return objectMapper.treeToValue(node, OpenAiApi.ChatCompletionRequest.class);
        } catch (IOException e) {
            log.warn("[ReasoningAwareOpenAiChatModel] Failed to patch reasoning_content, falling back to base request", e);
            return baseRequest;
        }
    }

    private Map<String, String> buildReasoningMap(Prompt prompt) {
        Map<String, String> map = new HashMap<>();
        for (Message message : prompt.getInstructions()) {
            if (message instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls()) {
                String reasoningContent = extractReasoningContent(assistantMessage);
                String key = assistantMessage.getToolCalls().stream()
                        .map(AssistantMessage.ToolCall::id)
                        .sorted()
                        .collect(Collectors.joining("#"));
                if (!key.isEmpty()) {
                    map.put(key, reasoningContent);
                    if (log.isDebugEnabled()) {
                        log.debug("[ReasoningAwareOpenAiChatModel] Registered reasoning_content for tool_call key={}: {}",
                                key,
                                reasoningContent.isEmpty() ? "<empty>" : reasoningContent.substring(0, Math.min(80, reasoningContent.length())) + "...");
                    }
                }
            }
        }
        return map;
    }

    private String toolCallIdsKey(JsonNode toolCallsNode) {
        if (toolCallsNode == null || !toolCallsNode.isArray()) {
            return "";
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode tc : toolCallsNode) {
            JsonNode idNode = tc.get("id");
            if (idNode != null && !idNode.isNull()) {
                ids.add(idNode.asText());
            }
        }
        ids.sort(String::compareTo);
        return String.join("#", ids);
    }

    private String extractReasoningContent(AssistantMessage assistantMessage) {
        if (assistantMessage.getMetadata() == null) {
            return "";
        }
        Object reasoning = assistantMessage.getMetadata().get("reasoningContent");
        if (reasoning == null) {
            reasoning = assistantMessage.getMetadata().get("reasoning_content");
        }
        return reasoning instanceof String s ? s : "";
    }
}
