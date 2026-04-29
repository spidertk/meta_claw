package meta.claw.gateway.channel;

import meta.claw.core.model.Context;
import meta.claw.core.model.Reply;

/**
 * Channel 接口
 * 消息发送渠道的抽象定义，所有具体渠道实现（如微信、Slack、Web、钉钉等）均需实现此接口。
 * 负责渠道的启动、停止、接收消息处理和回复发送。
 */
public interface Channel {

    /**
     * 获取当前渠道的类型标识
     *
     * @return 渠道类型字符串，例如：wechat、slack、web、dingtalk 等
     */
    String getChannelType();

    /**
     * 初始化并启动渠道
     * 完成渠道所需资源的初始化、连接建立、事件监听注册等操作。
     * 启动结果可通过内部事件机制或日志进行反馈。
     *
     * @throws Exception 启动过程中发生错误时抛出
     */
    void startup() throws Exception;

    /**
     * 优雅地停止渠道
     * 在系统重启或关闭前调用，用于释放连接、清理资源、保存状态等。
     */
    default void stop() {
        // 默认空实现，具体渠道可按需覆盖
    }

    /**
     * 处理接收到的文本消息
     * 将外部渠道的原始消息转换为内部 ChatMessage 后进行业务处理（如路由到 Agent、触发插件等）。
     *
     * @param msg 封装后的内部聊天消息对象
     */
    void handleText(ChatMessage msg);

    /**
     * 向用户或群组发送回复消息
     * 每个具体渠道需自行实现此方法，根据 reply 的 type 字段发送不同类型的消息（文本、图片、语音等）。
     *
     * @param reply   系统生成的回复对象，包含回复类型和内容
     * @param context 当前消息上下文，包含会话信息、渠道信息、扩展参数等
     */
    void send(Reply reply, Context context);
}
