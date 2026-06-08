package meta.claw.core.runtime.subsystem;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.MemoryConfig;
import meta.claw.core.memory.longterm.LongMemory;
import meta.claw.core.memory.longterm.LongMemoryFactory;
import meta.claw.core.memory.shortterm.ShortMemory;
import meta.claw.core.memory.shortterm.ShortMemoryFactory;
import meta.claw.core.prompt.PromptVars;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
}
