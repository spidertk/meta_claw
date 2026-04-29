package meta.claw.core.model;

/**
 * 回复类型枚举
 * 定义了系统可以产生的各种回复格式
 */
public enum ReplyType {
    /** 纯文本回复 */
    TEXT,
    /** 图片URL链接 */
    IMAGE_URL,
    /** 图片内容 */
    IMAGE,
    /** 语音回复 */
    VOICE,
    /** 文件回复 */
    FILE,
    /** 视频内容 */
    VIDEO,
    /** 视频URL链接 */
    VIDEO_URL,
    /** 错误信息回复 */
    ERROR,
    /** 提示信息回复 */
    INFO
}
