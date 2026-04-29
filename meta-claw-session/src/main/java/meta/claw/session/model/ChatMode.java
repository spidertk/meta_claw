package meta.claw.session.model;

/**
 * 聊天模式枚举
 * 定义了用户会话中支持的两种聊天交互模式
 */
public enum ChatMode {
    /**
     * 单聊模式：用户与单个专家代理一对一对话
     */
    SINGLE,

    /**
     * 群聊模式：用户在群组会话中与多个专家代理互动
     */
    GROUP
}
