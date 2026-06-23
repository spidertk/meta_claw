package meta.claw.core.config;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * HITL（Human-In-The-Loop）人工审批配置。
 * <p>
 * 同时用于全局配置（{@link GlobalConfig}）和 Vessel 级配置（{@link VesselConfig}）。
 * 全局配置作为默认值；Vessel 级配置中显式设置的字段会覆盖全局对应字段，
 * 未显式设置的字段（null）继承全局。
 * </p>
 *
 */
@Getter
@Setter
public class HitlConfig {

    /** 是否默认所有工具调用都需要审批（null=继承全局） */
    private Boolean defaultRequireApproval;

    /** 需要人工审批的工具名列表（null=继承全局；空列表表示显式清空） */
    private List<String> require;

    /** 自动跳过审批的工具名列表（null=继承全局；空列表表示显式清空） */
    private List<String> skip;
}
