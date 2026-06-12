package meta.claw.core.runtime.subsystem;

/**
 * 需要按 Vessel 加载一次配置的子系统。
 * <p>{@link meta.claw.core.runtime.VesselRuntime} 在创建后会为每个实现此接口的子系统调用
 * {@link #loadForVessel(String)}。</p>
 */
public interface VesselAwareSubSystem {
    void loadForVessel(String vesselId);
}
