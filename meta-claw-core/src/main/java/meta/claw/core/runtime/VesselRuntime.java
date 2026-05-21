package meta.claw.core.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.prompt.PromptContext;
import meta.claw.core.prompt.PromptContextManager;
import meta.claw.core.prompt.SystemPromptBuilder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Vessel 核心运行时类
 * <p>
 * 封装 Spring AI ChatClient，为每个 Vessel 提供独立的 AI 对话能力。
 * 通过 VesselConfig 中的系统提示词（systemPrompt）初始化 ChatClient，
 * 对外暴露统一的 chat 接口，负责将用户消息发送至 AI 模型并返回标准化的 Reply 对象。
 * </p>
 */
@Slf4j
@Component
public class VesselRuntime {

    @Autowired
    private  PromptContextManager promptContextManager;
    @Autowired
    private  SystemPromptBuilder systemPromptBuilder;
    @Autowired
    private LlmClientManager llmClientManager;



    private String resolveSystemPrompt(String vesselId) {

        try {
            PromptContext ctx = promptContextManager.create(vesselId);
            return systemPromptBuilder.build(ctx);
        } catch (Exception e) {
            log.warn("Failed to build system prompt for vessel {}: {}", vesselId, e.getMessage());
            return null;
        }
    }

    /**
     * 向 AI 模型发送用户消息，并返回标准化的回复对象。
     * @param vesselId vesselId
     * @param userMessage 用户输入的文本消息
     * @return 包含 AI 回复内容和元数据的标准化 Reply 对象
     */
    public Reply chat(String vesselId,String sessionId,String userMessage) {


        String systemPrompt = resolveSystemPrompt(vesselId);
        SpiChatRequest request = SpiChatRequest.builder()
                .messages(llmClientManager.buildLlmRequest( vesselId, sessionId, systemPrompt))
                .build();
        SpiChatResponse  response =llmClientManager.chat(request);

        String content = response != null && response.content() != null
                ? response.content()
                : "";

        return new Reply(ReplyType.TEXT, content);
    }


}
