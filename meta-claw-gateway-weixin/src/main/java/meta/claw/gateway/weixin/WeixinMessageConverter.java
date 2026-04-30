package meta.claw.gateway.weixin;

import com.openilink.model.WeixinMessage;
import com.openilink.util.MessageHelper;
import meta.claw.gateway.channel.ChatMessage;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 微信消息转换器
 * 负责将 openilink SDK 的原始 {@link WeixinMessage} 对象转换为系统内部统一的 {@link ChatMessage}。
 * 转换后的 ChatMessage 可直接进入 Gateway 的事件总线流程。
 */
public class WeixinMessageConverter {

    /**
     * 将 openilink WeixinMessage 转换为内部 ChatMessage
     *
     * @param msg openilink 原始消息对象
     * @return 封装后的内部聊天消息对象
     */
    public ChatMessage convert(WeixinMessage msg) {
        // 新版 SDK 中 groupId 不为空即代表群聊
        boolean group = msg.getGroupId() != null && !msg.getGroupId().isEmpty();

        return ChatMessage.builder()
                // 消息唯一标识（新版为 Long，转为 String）
                .msgId(msg.getMessageId() != null ? String.valueOf(msg.getMessageId()) : "")
                // 消息创建时间，新版为毫秒时间戳
                .createTime(msg.getCreateTimeMs() != null
                        ? Instant.ofEpochMilli(msg.getCreateTimeMs()).atZone(ZoneId.systemDefault()).toLocalDateTime()
                        : LocalDateTime.now())
                // 内容类型固定为文本
                .contentType("TEXT")
                // 提取纯文本内容
                .content(MessageHelper.extractText(msg))
                // 发送者用户 ID
                .fromUserId(msg.getFromUserId())
                // 发送者昵称（新版 SDK 已移除，用 userId 兜底）
                .fromUserNickname(msg.getFromUserId())
                // 接收者用户 ID
                .toUserId(msg.getToUserId())
                // 接收者昵称（新版 SDK 已移除，用 userId 兜底）
                .toUserNickname(msg.getToUserId())
                // 对方用户 ID：对于收到的消息，对方就是发送者
                .otherUserId(msg.getFromUserId())
                // 对方用户昵称
                .otherUserNickname(msg.getFromUserId())
                // 非自身发送的消息
                .myMsg(false)
                // 群聊标志：通过 groupId 判断
                .isGroup(group)
                // 是否 @ 机器人（新版 SDK 暂未提供，默认 false）
                .isAt(false)
                // 群聊中的实际发送者（新版 SDK 已移除，用 fromUserId 兜底）
                .actualUserId(msg.getFromUserId())
                .actualUserNickname(msg.getFromUserId())
                // @ 列表（新版 SDK 暂未提供）
                .atList(null)
                .build();
    }
}
