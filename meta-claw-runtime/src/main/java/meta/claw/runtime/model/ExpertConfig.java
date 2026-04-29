package meta.claw.runtime.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Expert 配置类
 * <p>
 * 对应 experts/ 目录下的 expert.yaml 配置文件，
 * 描述一个 AI Expert 的元数据、模型参数、系统提示词及会话配置。
 * </p>
 */
@Getter
@Setter
public class ExpertConfig {

    /**
     * Expert 唯一标识符
     */
    private String id;

    /**
     * Expert 显示名称
     */
    private String name;

    /**
     * Expert 功能描述
     */
    private String description;

    /**
     * Expert 表情符号（用于 UI 展示）
     */
    private String emoji;

    /**
     * AI 模型名称（如 gpt-4、claude-3 等）
     */
    private String model;

    /**
     * 系统提示词（System Prompt）
     */
    private String systemPrompt;

    /**
     * 是否开启记忆功能
     */
    private boolean memoryEnabled;

    /**
     * 知识库目录路径
     */
    private String knowledgeDir;

    /**
     * 需要排除的工具列表
     */
    private List<String> excludeTools;

    /**
     * 会话配置
     */
    private SessionConfig session;

}
