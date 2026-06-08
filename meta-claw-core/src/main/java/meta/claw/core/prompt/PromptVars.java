package meta.claw.core.prompt;

import lombok.Builder;

import java.util.HashMap;
import java.util.Map;

/**
 * Prompt 模板变量集合。
 * <p>子系统对 prompt 的贡献不是"一段文本"，而是往模板里填变量。</p>
 */
public class PromptVars {

    private final Map<String, String> vars;

    @Builder
    public PromptVars(Map<String, String> vars) {
        this.vars = vars != null ? Map.copyOf(vars) : Map.of();
    }

    public static PromptVars empty() {
        return new PromptVars(Map.of());
    }

    public static PromptVars of(String key, String value) {
        return new PromptVars(Map.of(key, value));
    }

    /** 合并另一个 PromptVars，返回新的（不可变） */
    public PromptVars merge(PromptVars other) {
        if (other == null || other.vars.isEmpty()) {
            return this;
        }
        Map<String, String> merged = new HashMap<>(this.vars);
        merged.putAll(other.vars);
        return new PromptVars(Map.copyOf(merged));
    }

    public Map<String, String> toMap() {
        return vars;
    }

    public String get(String key) {
        return vars.get(key);
    }

    public boolean isEmpty() {
        return vars.isEmpty();
    }
}
