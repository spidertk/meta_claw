package meta.claw.gateway.channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 渠道注册表
 * 负责管理系统中所有已注册的 Channel 实例，提供注册、查询、存在性判断等操作。
 * 使用 ConcurrentHashMap 保证线程安全，支持多线程环境下的并发读写。
 */
public class ChannelRegistry {

    /**
     * 渠道实例映射表，key 为渠道类型标识，value 为对应的 Channel 实例
     */
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();

    /**
     * 注册渠道实例
     * 将指定 Channel 以其类型标识为键存入注册表。若已存在相同类型的渠道，则覆盖原有实例。
     *
     * @param channel 待注册的渠道实例
     */
    public void register(Channel channel) {
        channels.put(channel.getChannelType(), channel);
    }

    /**
     * 根据渠道类型获取对应的 Channel 实例
     *
     * @param channelType 渠道类型标识，例如：weixin、slack、dingtalk 等
     * @return 对应的 Channel 实例，若不存在则返回 null
     */
    public Channel get(String channelType) {
        return channels.get(channelType);
    }

    /**
     * 判断指定类型的渠道是否已注册
     *
     * @param channelType 渠道类型标识
     * @return 若已注册返回 true，否则返回 false
     */
    public boolean hasChannel(String channelType) {
        return channels.containsKey(channelType);
    }
}
