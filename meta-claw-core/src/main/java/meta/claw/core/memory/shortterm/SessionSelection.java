package meta.claw.core.memory.shortterm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

/**
 * 会话选择结果。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSelection {
    /**
     * 会话 ID。
     */
    private String sessionId;

    /**
     * 历史文件路径。
     */
    private Path historyFilePath;
}
