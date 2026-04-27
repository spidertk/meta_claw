package com.meta_claw.knowledge.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * agent 角色到知识空间的绑定关系。
 * 用于把外部角色名解析为实际可访问的知识空间配置。
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentRoleBinding {
    /** 外部调用使用的角色名。 */
    private String roleName;
    /** 角色默认绑定的知识空间标识。 */
    private String spaceId;
    /** 知识空间的物理路径或挂载路径。 */
    private String spacePath;
    /** 当前空间继承的上游共享空间列表。 */
    private List<String> inherits;
    /** 创建该空间时采用的模板标识。 */
    private String templateId;
}
