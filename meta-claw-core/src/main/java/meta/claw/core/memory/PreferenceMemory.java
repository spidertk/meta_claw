package meta.claw.core.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 长期偏好记忆。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PreferenceMemory {
    private String id;
    private LocalDateTime timestamp;
    private String category;
    private String content;
    private Map<String, Object> metadata;
}
