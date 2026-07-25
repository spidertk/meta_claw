package meta.claw.gateway.weixin;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 微信渠道配置属性
 * <p>
 * 绑定 {@code meta.claw.weixin} 前缀的配置，最终形态为多账号列表：
 * 每个账号 = 1 个微信号 = 1 个 token = 1 个 WeixinChannel 实例。
 * 配置示例见 docs/weixin-channel-integration-design.md §4。
 * </p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "meta.claw.weixin")
public class WeixinProperties {

    /**
     * 渠道总开关；false 时不创建任何账号实例
     */
    private boolean enabled = false;

    /**
     * 账号列表（1 账号 = 1 微信号）
     */
    private List<Account> accounts = new ArrayList<>();

    /**
     * 单个微信账号配置
     */
    @Getter
    @Setter
    public static class Account {

        /**
         * 账号唯一标识（必填），channelKey = weixin:&lt;accountId&gt;
         */
        private String accountId;

        /**
         * 单账号开关
         */
        private boolean enabled = true;

        /**
         * openilink 认证令牌（可选）；状态目录存在 login.json 时以持久化为准
         */
        private String token;

        /**
         * openilink 服务基地址（可选），为空使用 SDK 默认地址
         */
        private String baseUrl;

        /**
         * 默认路由到的 vesselId（可选）；为空时由路由层回退到系统第一个 Vessel
         */
        private String defaultVesselId;

        /**
         * 私聊白名单（对端 userId）；空 = 不限制（仅建议调试期）
         */
        private List<String> allowFrom = new ArrayList<>();

        /**
         * 群聊绑定列表（P4 消费，本期仅定义形状）
         */
        private List<GroupBinding> groups = new ArrayList<>();
    }

    /**
     * 群聊绑定配置（P4）
     */
    @Getter
    @Setter
    public static class GroupBinding {

        /**
         * 群 ID
         */
        private String groupId;

        /**
         * 该群路由到的 vesselId
         */
        private String vesselId;

        /**
         * 触发前缀，默认 /ai
         */
        private String triggerPrefix = "/ai";
    }
}
