package meta.claw.core.llm.provider;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.api.OpenAiApi;

import java.io.IOException;

/**
 * Jackson Module，用于修补 Moonshot K2.5/K2.6 的 tool calling 兼容性问题。
 * <p>
 * Spring AI 1.1.4 在把 {@code AssistantMessage} 序列化为 {@link OpenAiApi.ChatCompletionMessage}
 * 时硬编码 {@code reasoningContent = null}，但 Moonshot 要求 assistant tool_call 消息必须包含
 * {@code reasoning_content} 字段（即使是空字符串）。
 * <p>
 * 本模块注册自定义序列化器，在序列化 {@link OpenAiApi.ChatCompletionMessage} 时自动检测并补上该字段。
 */
@Slf4j
public class MoonshotSerializerModule extends SimpleModule {

    // 干净的 ObjectMapper，不带自定义序列化器，用于执行默认序列化
    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper().findAndRegisterModules();

    public MoonshotSerializerModule() {
        super("moonshot-fix");
        addSerializer(OpenAiApi.ChatCompletionMessage.class, new ChatCompletionMessageSerializer());
    }

    /**
     * 自定义 ChatCompletionMessage 序列化器。
     * 先用默认方式序列化为 JsonNode，检查条件后修补 reasoning_content，再写回 JsonGenerator。
     */
    private static class ChatCompletionMessageSerializer extends JsonSerializer<OpenAiApi.ChatCompletionMessage> {

        @Override
        public void serialize(OpenAiApi.ChatCompletionMessage value, JsonGenerator gen,
                              SerializerProvider serializers) throws IOException {
            // 用干净的 mapper 把 value 转成 JsonNode（避免递归调用本序列化器）
            JsonNode node = DEFAULT_MAPPER.valueToTree(value);

            if (node instanceof ObjectNode objectNode) {
                String role = objectNode.path("role").asText("");
                boolean hasToolCalls = objectNode.has("tool_calls")
                        && objectNode.get("tool_calls").isArray()
                        && objectNode.get("tool_calls").size() > 0;
                boolean hasReasoning = objectNode.hasNonNull("reasoning_content");

                if ("assistant".equals(role) && hasToolCalls && !hasReasoning) {
                    objectNode.put("reasoning_content", "");
                    log.debug("[MoonshotSerializer] Patched reasoning_content for assistant tool_call message");
                }
            }

            // 将修补后的节点写回原始 JsonGenerator
            DEFAULT_MAPPER.writeValue(gen, node);
        }
    }
}
