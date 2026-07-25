package meta.claw.core.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 回复对象
 * 封装了系统处理消息后生成的回复内容及其类型
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Reply {

    /** 回复类型 */
    private ReplyType type;

    /** 回复内容 */
    private String content;

    /** 可选的文本内容，用于补充说明 */
    private String optionalTextContent;

    /** 可选的媒体文件本地路径（渠道出站媒体：图片/文件/视频），为 null 时按纯文本发送 */
    private String mediaPath;

    /** 可选的媒体类型提示：IMAGE / FILE / VIDEO（对应 ReplyType 无法表达媒体载体，故单独携带） */
    private String mediaType;

    /**
     * 便捷构造函数
     * 初始化回复类型和主要内容
     *
     * @param type    回复类型
     * @param content 回复内容
     */
    public Reply(ReplyType type, String content) {
        this.type = type;
        this.content = content;
    }
}
