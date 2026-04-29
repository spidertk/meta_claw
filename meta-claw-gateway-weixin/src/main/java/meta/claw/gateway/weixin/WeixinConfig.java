package meta.claw.gateway.weixin;

import lombok.Getter;
import lombok.Setter;

/**
 * 微信渠道配置类
 * 封装连接 openilink 微信 SDK 所需的全部配置参数，
 * 包括认证令牌、服务基地址以及监控端口。
 */
@Getter
@Setter
public class WeixinConfig {

    /**
     * openilink 平台分配的认证令牌，用于构建 ILinkClient
     */
    private String token;

    /**
     * openilink 服务基地址，可选配置，为空时使用 SDK 默认地址
     */
    private String baseUrl;

    /**
     * 本地监控服务端口，默认 8080
     */
    private int monitorPort = 8080;
}
