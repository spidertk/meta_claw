package meta.claw.core.model;

/**
 * 消息上下文类型枚举
 * 定义了消息在不同媒介和用途下的类型分类
 */
public enum ContextType {
    /** 文本消息 */
    TEXT,
    /** 语音消息 */
    VOICE,
    /** 图片消息 */
    IMAGE,
    /** 图片创建/生成请求 */
    IMAGE_CREATE,
    /** 分享内容 */
    SHARING,
    /** 文件消息 */
    FILE,
    /** 函数调用/功能请求 */
    FUNCTION
}
