package meta.claw.core.runtime;

/**
 * Vessel 上下文持有器，基于 ThreadLocal 传递当前 vessel ID 与任务上下文。
 * <p>
 * 在 Agent 执行工具调用前通过 {@link #bind(TaskContext)} 与当前任务上下文显式绑定，
 * 工具执行完毕后调用 {@link #unbind()} 清除。工具实现可通过 {@link #getVesselId()}
 * 获取当前 vessel ID（按 vessel 隔离的数据存储），或通过 {@link #getTaskContext()}
 * 获取完整任务上下文（如 sendMedia 工具向当前回复挂载待发媒体）。
 * </p>
 */
public final class VesselContext {

    private static final ThreadLocal<String> VESSEL_ID = new ThreadLocal<>();
    private static final ThreadLocal<TaskContext> TASK_CTX = new ThreadLocal<>();

    private VesselContext() {
    }

    /**
     * 绑定当前任务上下文，将其 vesselId 与上下文引用写入 ThreadLocal。
     */
    public static void bind(TaskContext ctx) {
        if (ctx != null) {
            TASK_CTX.set(ctx);
            if (ctx.getVesselId() != null) {
                VESSEL_ID.set(ctx.getVesselId());
            }
        }
    }

    /**
     * 直接设置当前 vessel ID（非 Agent 链路或测试可用）。
     */
    public static void setVesselId(String vesselId) {
        VESSEL_ID.set(vesselId);
    }

    public static String getVesselId() {
        return VESSEL_ID.get();
    }

    /**
     * 获取当前绑定的任务上下文；未绑定（非 Agent 链路）时返回 null。
     */
    public static TaskContext getTaskContext() {
        return TASK_CTX.get();
    }

    public static void unbind() {
        VESSEL_ID.remove();
        TASK_CTX.remove();
    }

    public static void clear() {
        VESSEL_ID.remove();
        TASK_CTX.remove();
    }
}
