package meta.claw.gateway.channel;

/**
 * 渠道工厂
 * 负责根据渠道类型创建对应的 Channel 实例。
 * P1 阶段仅预留 weixin 类型的扩展入口，具体实现由 gateway-weixin 模块通过 Spring 注入提供。
 * 后续可在此扩展支持 slack、dingtalk、web 等更多渠道类型。
 */
public class ChannelFactory {

    /**
     * 根据渠道类型创建对应的 Channel 实例
     * P1 阶段仅支持 weixin 类型标识的声明，实际 Channel 实例需通过 Spring Bean 注入，
     * 因此调用 weixin 相关类型时会抛出 UnsupportedOperationException。
     *
     * @param channelType 渠道类型标识，例如：weixin、slack、dingtalk 等
     * @return 对应的 Channel 实例
     * @throws UnsupportedOperationException 若渠道类型为 weixin 或 wx，提示使用 Spring Bean 注入
     * @throws IllegalArgumentException      若传入未知的渠道类型
     */
    public Channel createChannel(String channelType) {
        // P1 仅支持 weixin，后续扩展其他渠道类型
        if ("weixin".equals(channelType) || "wx".equals(channelType)) {
            // WeixinChannel 在 gateway-weixin 模块中，通过 Spring 注入，不通过工厂直接创建
            throw new UnsupportedOperationException("Use Spring Bean for WeixinChannel");
        }
        throw new IllegalArgumentException("Unknown channel type: " + channelType);
    }
}
