package meta.claw.gateway.weixin;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微信渠道配置启用类
 * <p>启用 {@link WeixinProperties} 的配置绑定（meta.claw.weixin 前缀）。</p>
 */
@Configuration
@EnableConfigurationProperties(WeixinProperties.class)
public class WeixinChannelConfiguration {
}
