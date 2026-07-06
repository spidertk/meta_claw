package meta.claw.core.runtime;

/**
 * Vessel 上下文持有器，基于 ThreadLocal 传递当前 vessel ID。
 * <p>
 * 在 Agent 执行工具调用前通过 {@link #bind(TaskContext)} 与当前任务上下文显式绑定，
 * 工具执行完毕后调用 {@link #unbind()} 清除。工具实现可通过 {@link #getVesselId()}
 * 获取当前 vessel ID，以实现按 vessel 隔离的数据存储（如知识库）。
 * </p>
 */
public final class VesselContext {

    private static final ThreadLocal<String> VESSEL_ID = new ThreadLocal<>();

    private VesselContext() {
    }

    /**
     * 绑定当前任务上下文，将其 vesselId 写入 ThreadLocal。
     */
    public static void bind(TaskContext ctx) {
        if (ctx != null && ctx.getVesselId() != null) {
            VESSEL_ID.set(ctx.getVesselId());
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

    public static void unbind() {
        VESSEL_ID.remove();
    }

    public static void clear() {
        VESSEL_ID.remove();
    }
}
