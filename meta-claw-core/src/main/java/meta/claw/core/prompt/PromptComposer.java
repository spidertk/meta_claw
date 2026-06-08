package meta.claw.core.prompt;

import meta.claw.core.runtime.subsystem.SubSystemRegistry;
import meta.claw.core.runtime.subsystem.VesselSubSystem;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/**
 * Prompt 变量组装器。
 * <p>从 SubSystemRegistry 收集所有子系统的 promptVars，按 priority 排序后 merge。</p>
 */
@Component
public class PromptComposer {

    /**
     * 从 registry 收集所有子系统（含内置的 profile）的 promptVars，
     * 按 priority 排序后 merge 成一份完整的静态变量集合。
     */
    public PromptVars compose(SubSystemRegistry registry) {
        return registry.listAll().stream()
                .sorted(Comparator.comparingInt(VesselSubSystem::priority))
                .map(VesselSubSystem::promptVars)
                .reduce(PromptVars.empty(), PromptVars::merge);
    }
}
