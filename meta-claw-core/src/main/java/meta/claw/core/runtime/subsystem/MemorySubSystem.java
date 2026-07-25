package meta.claw.core.runtime.subsystem;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.MemoryConfig;
import meta.claw.core.memory.PreferenceMemory;
import meta.claw.core.memory.longterm.LongMemory;
import meta.claw.core.memory.longterm.LongMemoryFactory;
import meta.claw.core.memory.shortterm.ShortMemory;
import meta.claw.core.memory.shortterm.ShortMemoryFactory;
import meta.claw.core.prompt.PromptVars;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Memory 子系统。
 * <p>包装现有 ShortMemoryFactory + LongMemoryFactory，通过 SPI 接入 VesselRuntime。</p>
 */
@Slf4j
@Component
public class MemorySubSystem implements VesselSubSystem {

    @Autowired
    private ShortMemoryFactory shortMemoryFactory;
    @Autowired
    private LongMemoryFactory longMemoryFactory;

    private SubSystemRegistry registry;

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public void configure(SubSystemRegistry registry) {
        this.registry = registry;
    }

    @Override
    public PromptVars promptVars() {
        // Phase 1: 暂无动态注入。Phase 5+ 可注入长期记忆摘要。
        return PromptVars.empty();
    }

    @Override
    public int priority() {
        return 10;
    }

    public ShortMemory getShortMemory(MemoryConfig config) {
        return shortMemoryFactory.get(config.getShortTermStore());
    }

    public LongMemory getLongMemory(MemoryConfig config) {
        return longMemoryFactory.get(config.getLongTermStore());
    }

    /** 注入 system prompt 的偏好摘要条数上限（Letta core-memory 式：少量、高价值、常驻） */
    private static final int PROMPT_PREFERENCE_LIMIT = 10;

    /**
     * 构建注入 system prompt 的用户偏好摘要（每轮构建 prompt 时实时读取，新存偏好下一轮即生效）。
     *
     * @return 形如 {@code - [preference] 用户喜欢简洁回答} 的多行文本；无偏好或读取失败返回空串（区块自动折叠）
     */
    public String buildPreferencesSummary(String vesselId, MemoryConfig config) {
        try {
            List<PreferenceMemory> recent = getLongMemory(config).listRecentPreferences(vesselId, PROMPT_PREFERENCE_LIMIT);
            if (recent.isEmpty()) {
                return "";
            }
            return recent.stream()
                    .map(p -> "- [" + (p.getCategory() != null ? p.getCategory() : "preference") + "] " + p.getContent())
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("Failed to build preferences summary for vessel {}: {}", vesselId, e.getMessage());
            return "";
        }
    }
}
