package meta.claw.gateway.weixin;

import com.openilink.model.Message;
import com.openilink.util.MessageHelper;
import meta.claw.gateway.channel.ChatMessage;

import java.time.LocalDateTime;

/**
 * 微信消息转换器
 * 负责将 openilink SDK 的原始 {@link Message} 对象转换为系统内部统一的 {@link ChatMessage}。
 * 转换后的 ChatMessage 可直接进入 Gateway 的事件总线流程。
 */
public class WeixinMessageConverter {

    /**
     * 将 openilink Message 转换为内部 ChatMessage
     *
     * @param msg openilink 原始消息对象
     * @return 封装后的内部聊天消息对象
     */
    public ChatMessage convert(Message msg) {
        return ChatMessage.builder()
                // 消息唯一标识
                .msgId(msg.getMsgId())
                // 消息创建时间，使用当前时间作为兜底
                .createTime(msg.getCreateTime() != null ? msg.getCreateTime() : LocalDateTime.now())
                // 内容类型固定为文本（openilink 文本消息）
                .contentType("TEXT")
                // 提取纯文本内容
                .content(MessageHelper.extractText(msg))
                // 发送者用户 ID
                .fromUserId(msg.getFromUserId())
                // 发送者昵称（若 SDK 提供）
                .fromUserNickname(msg.getFromUserNickname())
                // 接收者用户 ID（当前机器人）
                .toUserId(msg.getToUserId())
                // 接收者昵称
                .toUserNickname(msg.getToUserNickname())
                // 对方用户 ID：对于收到的消息，对方就是发送者
                .otherUserId(msg.getFromUserId())
                // 对方用户昵称
                .otherUserNickname(msg.getFromUserNickname())
                // 非自身发送的消息
                .myMsg(false)
                // 群聊标志，由 SDK 消息提供
                .isGroup(msg.isGroup())
                // 是否 @ 机器人，由 SDK 消息提供
                .isAt(msg.isAt())
                // 群聊中的实际发送者信息
                .actualUserId(msg.getActualUserId())
                .actualUserNickname(msg.getActualUserNickname())
                // @ 列表
                .atList(msg.getAtList())
                .build();
    }
}
