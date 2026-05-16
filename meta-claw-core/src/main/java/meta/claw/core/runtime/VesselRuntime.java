package meta.claw.core.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.prompt.PromptContext;
import meta.claw.core.prompt.PromptContextFactory;
import meta.claw.core.prompt.SystemPromptBuilder;
import meta.claw.core.prompt.TemplateLoader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.nio.file.Path;
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
public class VesselRuntime {

    /**
     * Vessel 配置对象，包含系统提示词、模型参数等元数据
     */
    private final VesselConfig config;

    /**
     * Spring AI 对话客户端，负责与底层 AI 模型进行交互
     */
    private final ChatClient chatClient;

    /**
     * 构造方法：根据 Vessel 配置和 ChatClient 初始化运行时实例
     *
     * @param config     Vessel 配置对象，包含系统提示词等元数据
     * @param chatClient Spring AI ChatClient，底层 AI 模型对话客户端
     */
    public VesselRuntime(VesselConfig config, ChatClient chatClient) {
        this.config = config;
        this.chatClient = chatClient;
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
        // Phase 2: Fall back to SystemPromptBuilder if no static systemPrompt configured
        try {
            PromptContextFactory factory = new PromptContextFactory();
            PromptContext ctx = factory.create(config, Path.of("."), null);
            SystemPromptBuilder builder = new SystemPromptBuilder(new TemplateLoader());
            return builder.build(ctx);
        } catch (Exception e) {
            log.warn("Failed to build dynamic system prompt for vessel {}, fallback to null", config.getId(), e);
            return null;
        }
    }

    /**
     * 执行用户对话
     * <p>
     * 将用户消息通过 ChatClient 发送至 AI 模型，获取文本回复。
     * 若调用成功，返回 TEXT 类型的 Reply；若发生异常，返回 ERROR 类型的 Reply，
     * 并记录错误日志以便排查问题。
     * </p>
     *
     * @param userMessage 用户输入的消息内容
     * @return 标准化的 Reply 对象，包含 AI 回复文本或错误信息
     */
    public Reply chat(String userMessage) {
        log.debug("Vessel 开始处理用户消息: vesselId={}, messageLength={}",
                config != null ? config.getId() : "null", userMessage != null ? userMessage.length() : 0);

        try {
            // 通过 ChatClient 调用 AI 模型获取回复内容
            // 若配置了 systemPrompt，则先注入 SystemMessage
            String response;
            String systemPrompt = resolveSystemPrompt(config);
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                Prompt prompt = new Prompt(List.of(
                        new SystemMessage(systemPrompt),
                        new UserMessage(userMessage)
                ));
                ChatResponse chatResponse = chatClient.prompt(prompt).call().chatResponse();
                response = safeExtractContent(chatResponse);
            } else {
                response = chatClient.prompt().user(userMessage).call().content();
            }

            log.debug("Vessel 成功获取 AI 回复: vesselId={}, responseLength={}",
                    config != null ? config.getId() : "null", response != null ? response.length() : 0);

            // 返回文本类型的标准化回复对象
            return new Reply(ReplyType.TEXT, response);
        } catch (Exception e) {
            // 记录异常日志，包含 Vessel 标识和异常详情，便于后续问题定位
            log.error("Vessel 对话调用异常: vesselId={}, errorMessage={}",
                    config != null ? config.getId() : "null", e.getMessage(), e);

            // 返回错误类型的标准化回复对象，向用户展示友好的错误提示
            return new Reply(ReplyType.ERROR, "服务异常，请稍后重试");
        }
    }

    /**
     * 获取当前 Vessel 的唯一标识
     *
     * @return Vessel 的 id，对应配置文件中的 id 字段
     */
    public String getVesselId() {
        return config != null ? config.getId() : "null";
    }

    private String safeExtractContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            log.warn("VesselRuntime 收到空响应或响应结构不完整");
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text != null ? text : "";
    }
}
