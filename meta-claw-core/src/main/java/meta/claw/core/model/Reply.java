package meta.claw.core.model;

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
