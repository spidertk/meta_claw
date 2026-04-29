package meta.claw.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.Reply;
import meta.claw.core.model.ReplyType;
import meta.claw.runtime.model.ExpertConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Expert 核心运行时类
 * <p>
 * 封装 Spring AI ChatClient，为每个 Expert 提供独立的 AI 对话能力。
 * 通过 ExpertConfig 中的系统提示词（systemPrompt）初始化 ChatClient，
 * 对外暴露统一的 chat 接口，负责将用户消息发送至 AI 模型并返回标准化的 Reply 对象。
 * </p>
 */
@Slf4j
public class ExpertRuntime {

    /**
     * Expert 配置对象，包含系统提示词、模型参数等元数据
     */
    private final ExpertConfig config;

    /**
     * Spring AI 对话客户端，负责与底层 AI 模型进行交互
     */
    private final ChatClient chatClient;

    /**
     * 构造方法：根据 Expert 配置和 ChatModel 初始化运行时实例
     * <p>
     * 使用 ChatClient.builder 构建器模式创建 ChatClient 实例，
     * 并将配置中的系统提示词（systemPrompt）设置为默认系统消息。
     * </p>
     *
     * @param config    Expert 配置对象，包含系统提示词等元数据
     * @param chatModel Spring AI ChatModel，底层 AI 模型接口
     */
    public ExpertRuntime(ExpertConfig config, ChatModel chatModel) {
        this.config = config;
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(config.getSystemPrompt())
                .build();
        log.info("ExpertRuntime 初始化完成: expertId={}, model={}, systemPromptLength={}",
                config.getId(), config.getModel(),
                config.getSystemPrompt() != null ? config.getSystemPrompt().length() : 0);
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
        log.debug("Expert 开始处理用户消息: expertId={}, messageLength={}",
                config.getId(), userMessage != null ? userMessage.length() : 0);

        try {
            // 通过 ChatClient 构建 Prompt 并调用 AI 模型获取回复内容
            String response = chatClient.prompt()
                    .user(userMessage)
                    .call()
                    .content();

            log.debug("Expert 成功获取 AI 回复: expertId={}, responseLength={}",
                    config.getId(), response != null ? response.length() : 0);

            // 返回文本类型的标准化回复对象
            return new Reply(ReplyType.TEXT, response);
        } catch (Exception e) {
            // 记录异常日志，包含 Expert 标识和异常详情，便于后续问题定位
            log.error("Expert 对话调用异常: expertId={}, errorMessage={}",
                    config.getId(), e.getMessage(), e);

            // 返回错误类型的标准化回复对象，向用户展示友好的错误提示
            return new Reply(ReplyType.ERROR, "服务异常，请稍后重试");
        }
    }

    /**
     * 获取当前 Expert 的唯一标识
     *
     * @return Expert 的 id，对应配置文件中的 id 字段
     */
    public String getExpertId() {
        return config.getId();
    }

}
