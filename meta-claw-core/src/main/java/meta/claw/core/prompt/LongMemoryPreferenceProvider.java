package meta.claw.core.prompt;

import lombok.RequiredArgsConstructor;
import meta.claw.core.memory.PreferenceMemory;
import meta.claw.core.memory.longterm.LongMemoryStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 {@link LongMemoryStore} 的 {@link PreferenceProvider} 实现。
 * <p>
 * 注意：此类不是 Spring 组件，因为它依赖的 {@link LongMemoryStore}
 * 需要运行时参数（如 vesselsDir）才能实例化。由调用方在需要时手动创建。
 * </p>
 */
@RequiredArgsConstructor
public class LongMemoryPreferenceProvider implements PreferenceProvider {

    private final LongMemoryStore longMemoryStore;

    @Override
    public String getPreferences(String vesselId) {
        if (vesselId == null || longMemoryStore == null) {
            return "";
        }
        List<PreferenceMemory> entries = longMemoryStore.listRecentPreferences(vesselId, 20);
        if (entries.isEmpty()) {
            return "";
        }
        return entries.stream()
                .map(e -> "- " + orDefault(e.getContent(), ""))
                .collect(Collectors.joining("\n"));
    }

    private static String orDefault(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }
}
