package meta.claw.core.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiMessage;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.memory.MemoryMessage;
import meta.claw.core.memory.MemoryMessageConverter;
import meta.claw.core.memory.longterm.LongMemory;
import meta.claw.core.memory.longterm.LongMemoryFactory;
import meta.claw.core.memory.shortterm.ShortMemory;
import meta.claw.core.memory.shortterm.ShortMemoryFactory;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.prompt.PromptContext;
import meta.claw.core.prompt.PromptContextFactory;
import meta.claw.core.prompt.PromptRenderer;
import org.apache.commons.lang3.StringUtils;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
public class VesselRuntime implements InitializingBean {

    @Autowired
    private PromptContextFactory promptContextManager;
    @Autowired
    private PromptRenderer promptRenderer;
    @Autowired
    private LlmClientManager llmClient;
    @Autowired
    private ShortMemoryFactory shortMemory;
    @Autowired
    private LongMemoryFactory longMemory;

    private  PromptContext  promptContext;


    private final String vesselId;

    public VesselRuntime(String vesselId) {
        this.vesselId = vesselId;

    }

    /**
     * 创建当前 Vessel 的 PromptContext（无参，自动使用 this.vesselId）
     */
    public PromptContext createPromptContext() {
        return promptContext;
    }

    /**
     * 便捷方法：获取当前 Vessel 的短期记忆
     */
    public ShortMemory getShortMemory() {
        return shortMemory.get(promptContext.getBundle().getMemoryConfig().getShortTermStore());
    }

    /**
     * 便捷方法：获取当前 Vessel 的长期记忆
     */
    public LongMemory getLongMemory() {
        return longMemory.get(promptContext.getBundle().getMemoryConfig().getLongTermStore());
    }

    /**
     * 获取 Vessel 配置
     */
    public VesselConfig getConfig() {
        return promptContext.getBundle().getRuntimeVesselConfig();
    }
    /**
     * 将用户消息转换成 ChatClient 所需的格式。
     * @param userMessage 用户输入消息
     * @param sessionId 会话ID
     * @return 转换后的 ChatClient 所需的格式
     */
    private List<SpiMessage> buildLlmRequest(String userMessage,String sessionId, String systemPrompt) {
        List<SpiMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SpiMessage.system(systemPrompt));
        }
        if (StringUtils.isNotBlank(sessionId)){
            messages.addAll(toSpiMessages(getShortMemory().loadMessages( vesselId, sessionId,promptContext.getBundle().getMaxHistoryRounds())));
        }
        messages.add(SpiMessage.user(userMessage));
        getShortMemory().appendMessage(  vesselId, sessionId,
                MemoryMessageConverter.fromSpiMessage(SpiMessage.user(userMessage)));
        return messages;
    }


    private String resolveSystemPrompt() {

        try {
            PromptContext ctx = promptContextManager.create(vesselId);
            return promptRenderer.renderSystem(ctx);
        } catch (Exception e) {
            log.warn("Failed to build system prompt for vessel {}: {}", vesselId, e.getMessage());
            return null;
        }
    }

    /**
     * 向 AI 模型发送用户消息，并返回标准化的回复对象。
     * @param userMessage 用户输入的文本消息
     * @return 包含 AI 回复内容和元数据的标准化 Reply 对象
     */
    public Reply chat(String sessionId,String userMessage) {

        String systemPrompt = resolveSystemPrompt();
        SpiChatRequest request = SpiChatRequest.builder()
                .messages(buildLlmRequest( userMessage,sessionId,systemPrompt))
                .ctx( promptContext)
                .sessionId(sessionId)
                .build();
        SpiChatResponse  response =llmClient.chat(request);

        String content = response != null && response.content() != null
                ? response.content()
                : "";

        return new Reply(ReplyType.TEXT, content);
    }

    /**
     * 优雅关闭运行时，释放资源。
     * <p>默认空实现，子类可按需覆盖。</p>
     */
    public void shutdown() {
        log.info("VesselRuntime shutdown: {}", vesselId);
    }

    public void chatStream(String sessionId,String userMessage, SpiStreamingCallback callback) {

        SpiChatRequest request = SpiChatRequest.builder()
                .messages(buildLlmRequest( userMessage,sessionId, resolveSystemPrompt()))
                .ctx( promptContext)
                .sessionId(sessionId)
                .build();
        llmClient.chatStream(request,callback);
    }

    private List<SpiMessage> toSpiMessages(List<MemoryMessage> entries) {
        List<SpiMessage> restored = new ArrayList<>();
        for (MemoryMessage entry : entries) {
            SpiMessage message = MemoryMessageConverter.toSpiMessage(entry);
            if (message.getRole() == null) {
                continue;
            }
            switch (message.getRole().toLowerCase()) {
                case "user" -> restored.add(SpiMessage.user(message.getContent()));
                case "assistant" -> restored.add(
                        SpiMessage.assistant(message.getContent(), message.getReasoningContent(), message.getToolCalls()));
                case "tool" -> restored.add(SpiMessage.tool(message.getContent()));
                default -> {
                    // System prompts are rebuilt from current vessel config when resuming.
                }
            }
        }
        return restored;
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        this.promptContext = promptContextManager.create(vesselId);;
    }
}
