package meta.claw.gateway.weixin;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.infra.ProjectRootFinder;
import meta.claw.gateway.Gateway;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信渠道管理器
 * <p>
 * 按 {@link WeixinProperties} 的账号列表创建并管理 WeixinChannel 实例：
 * 1 账号 = 1 微信号 = 1 token = 1 Channel 实例，状态目录按 accountId 隔离。
 * 同时作为管理端点（状态查询、取二维码、触发重登录）的入口。
 * </p>
 */
@Slf4j
@Component
public class WeixinChannelManager {

    private final WeixinProperties properties;
    private final Gateway gateway;
    private final WeixinMessageConverter converter;

    /**
     * accountId → 渠道实例
     */
    private final Map<String, WeixinChannel> channels = new LinkedHashMap<>();

    public WeixinChannelManager(WeixinProperties properties, Gateway gateway, WeixinMessageConverter converter) {
        this.properties = properties;
        this.gateway = gateway;
        this.converter = converter;
    }

    @PostConstruct
    public void start() {
        if (!properties.isEnabled()) {
            log.info("[WeixinChannelManager] 微信渠道未启用(meta.claw.weixin.enabled=false)，跳过");
            return;
        }
        if (properties.getAccounts().isEmpty()) {
            log.warn("[WeixinChannelManager] 微信渠道已启用但未配置任何账号(meta.claw.weixin.accounts)");
            return;
        }
        for (WeixinProperties.Account account : properties.getAccounts()) {
            if (!account.isEnabled()) {
                log.info("[WeixinChannelManager] 账号 {} 未启用，跳过", account.getAccountId());
                continue;
            }
            if (account.getAccountId() == null || account.getAccountId().isBlank()) {
                log.error("[WeixinChannelManager] 存在缺少 account-id 的账号配置，已跳过");
                continue;
            }
            if (account.getAllowFrom().isEmpty()) {
                log.warn("[WeixinChannelManager] 账号 {} 未配置 allow-from 白名单，不限制入站来源（仅建议调试期）",
                        account.getAccountId());
            }
            Path stateDir = ProjectRootFinder.getMetaClawDir()
                    .resolve("channels").resolve("weixin").resolve(account.getAccountId());
            WeixinChannel channel = new WeixinChannel(account, new WeixinStateStore(stateDir), gateway, converter);
            channels.put(account.getAccountId(), channel);
            gateway.registerChannel(channel);
        }
        log.info("[WeixinChannelManager] 微信渠道启动完成, 账号数={}", channels.size());
    }

    @PreDestroy
    public void shutdown() {
        channels.values().forEach(WeixinChannel::stop);
        channels.clear();
    }

    /**
     * 获取指定账号的渠道实例
     *
     * @return 渠道实例；不存在返回 null
     */
    public WeixinChannel getChannel(String accountId) {
        return channels.get(accountId);
    }

    /**
     * 全部渠道实例（accountId → channel）
     */
    public Map<String, WeixinChannel> listChannels() {
        return Collections.unmodifiableMap(channels);
    }
}
