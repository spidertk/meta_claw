package meta.claw.core.runtime.subsystem;

import meta.claw.core.prompt.PromptVars;
import meta.claw.core.runtime.TaskContext;

/**
 * Vessel 子系统 SPI。
 * 所有能力（Memory, Tool, Skill, HITL, Metrics, Knowledge, Cron）必须实现此接口，
 * 由 VesselRuntime 统一编排生命周期。
 *
 * <p>调用顺序（由 VesselRuntime 保证）：</p>
 * <ol>
 *   <li>{@link #configure(SubSystemRegistry)} — VesselRuntime 创建时调用一次</li>
 *   <li>{@link #promptVars()} — 每次任务前，收集 prompt 变量时调用</li>
 *   <li>{@link #onTaskStart(TaskContext)} — 每次任务开始时调用</li>
 *   <li>{@link #onTaskEnd(TaskContext)} — 每次任务结束时调用（finally 中）</li>
 * </ol>
 */
public interface VesselSubSystem {

    /** 子系统唯一标识，如 "memory", "tool", "skill", "hitl" */
    String name();

    /**
     * 配置阶段：VesselRuntime 创建时调用一次。
     * <p>用途：</p>
     * <ul>
     *   <li>缓存 {@link SubSystemRegistry} 引用</li>
     *   <li>建立外部连接（如 MCP 客户端初始化）</li>
     *   <li>加载一次性的索引数据（如技能文件索引）</li>
     * </ul>
     */
    void configure(SubSystemRegistry registry);

    /**
     * 返回本系统对本次任务 prompt 的贡献变量。
     * 多个子系统的 PromptVars 会被 VesselRuntime 通过 {@link PromptVars#merge} 合并。
     */
    default PromptVars promptVars() {
        return PromptVars.empty();
    }

    /** 任务开始 */
    default void onTaskStart(TaskContext ctx) {}

    /** 任务结束 */
    default void onTaskEnd(TaskContext ctx) {}

    /** 优先级：数值越小，越早执行 promptVars */
    default int priority() {
        return 100;
    }
}
