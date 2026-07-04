package meta.claw.core.runtime;

/**
 * Vessel 上下文持有器，基于 ThreadLocal 传递当前 vessel ID。
 * <p>
 * 在 Agent 执行工具调用前设置，工具执行完毕后清除。
 * 工具实现可通过 {@link #getVesselId()} 获取当前 vessel ID，
 * 以实现按 vessel 隔离的数据存储（如知识库）。
 * </p>
 */
public final class VesselContext {

    private static final ThreadLocal<String> VESSEL_ID = new ThreadLocal<>();

    private VesselContext() {
    }

    public static void setVesselId(String vesselId) {
        VESSEL_ID.set(vesselId);
    }

    public static String getVesselId() {
        return VESSEL_ID.get();
    }

    public static void clear() {
        VESSEL_ID.remove();
    }
}