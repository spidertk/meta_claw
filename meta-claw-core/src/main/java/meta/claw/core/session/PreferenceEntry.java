package meta.claw.core.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户偏好条目
 * 记录用户偏好、个人习惯、工具使用模式等非领域知识信息
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PreferenceEntry {

    /**
     * 偏好条目唯一标识
     */
    private String id;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 偏好类别：preference / fact / tool_usage / context
     */
    private String category;

    /**
     * 偏好内容
     */
    private String content;

    /**
     * 扩展元数据
     */
    private Map<String, Object> metadata;
}
