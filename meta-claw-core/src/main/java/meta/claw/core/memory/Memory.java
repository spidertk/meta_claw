package meta.claw.core.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 记忆聚合根。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Memory {
    private List<SessionMemory> sessions;
    private List<PreferenceMemory> preferences;
}
