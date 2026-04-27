package com.meta_claw.knowledge.core.api.req;

/**
 * 具备 roleName 的请求协议。
 * 用于在不同 flow context 之间复用统一的 knowledge space 绑定逻辑。
 */
public interface RoleScopedRequest {
    String getRoleName();
}
