package meta.claw.core.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.prompt.PromptContext;
import meta.claw.core.prompt.PromptContextFactory;
import meta.claw.core.prompt.SystemPromptBuilder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.nio.file.Path;
import java.util.List;
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
@Scope("prototype")
public class VesselRuntime {

    private final VesselConfig config;
    private final ChatClient chatClient;
    private final PromptContextFactory promptContextFactory;
    private final SystemPromptBuilder systemPromptBuilder;

    public VesselRuntime(VesselConfig config, ChatClient chatClient,
                         PromptContextFactory promptContextFactory,
                         SystemPromptBuilder systemPromptBuilder) {
        this.config = config;
        this.chatClient = chatClient;
        this.promptContextFactory = promptContextFactory;
        this.systemPromptBuilder = systemPromptBuilder;
        if (config != null) {
            log.info("VesselRuntime 初始化完成: vesselId={}, model={}, systemPromptLength={}",
                    config.getId(), config.getModel(),
                    resolveSystemPrompt(config) != null ? resolveSystemPrompt(config).length() : 0);
        } else {
            log.info("VesselRuntime 初始化完成: config=null");
        }
    }

    private String resolveSystemPrompt(VesselConfig config) {
        if (config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            return config.getSystemPrompt();
        }
        try {
            PromptContext ctx = promptContextFactory.create(config);
            return systemPromptBuilder.build(ctx);
        } catch (Exception e) {
            log.warn("Failed to build system prompt for vessel {}: {}", config.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * 向 AI 模型发送用户消息，并返回标准化的回复对象。
     *
     * @param userMessage 用户输入的文本消息
     * @return 包含 AI 回复内容和元数据的标准化 Reply 对象
     */
    public Reply chat(String userMessage) {
        if (chatClient == null) {
            return new Reply(ReplyType.TEXT, "ChatClient 未初始化，无法处理消息。");
        }

        String systemPrompt = resolveSystemPrompt(config);

        ChatResponse response;
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .chatResponse();
        } else {
            response = chatClient.prompt()
                    .user(userMessage)
                    .call()
                    .chatResponse();
        }

        String content = response != null && response.getResult() != null
                ? response.getResult().getOutput().getText()
                : "";

        return new Reply(ReplyType.TEXT, content);
    }

    public VesselConfig getConfig() {
        return config;
    }
}
