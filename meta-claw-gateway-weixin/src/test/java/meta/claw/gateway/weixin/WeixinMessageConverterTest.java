package meta.claw.gateway.weixin;

import com.openilink.model.MessageItem;
import com.openilink.model.MessageItemType;
import com.openilink.model.TextItem;
import com.openilink.model.WeixinMessage;
import meta.claw.gateway.channel.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WeixinMessageConverter 单元测试：文本提取、群聊标记、字段映射
 */
class WeixinMessageConverterTest {

    private final WeixinMessageConverter converter = new WeixinMessageConverter();

    private WeixinMessage textMsg(String text) {
        return WeixinMessage.builder()
                .messageId(123L)
                .fromUserId("wxid_sender")
                .toUserId("bot-id")
                .createTimeMs(1700000000000L)
                .itemList(List.of(MessageItem.builder()
                        .type(MessageItemType.TEXT)
                        .textItem(TextItem.builder().text(text).build())
                        .build()))
                .build();
    }

    @Test
    void convertsTextMessage() {
        ChatMessage result = converter.convert(textMsg("你好"));
        assertEquals("你好", result.getContent());
        assertEquals("TEXT", result.getContentType());
        assertEquals("123", result.getMsgId());
        assertEquals("wxid_sender", result.getFromUserId());
        assertEquals("wxid_sender", result.getOtherUserId());
        assertFalse(result.isGroup());
        assertFalse(result.isAt());
    }

    @Test
    void groupIdSetsGroupFlag() {
        WeixinMessage msg = textMsg("群里说话");
        msg.setGroupId("group-1");
        ChatMessage result = converter.convert(msg);
        assertTrue(result.isGroup());
    }

    @Test
    void missingMessageIdYieldsEmptyString() {
        WeixinMessage msg = textMsg("hi");
        msg.setMessageId(null);
        assertEquals("", converter.convert(msg).getMsgId());
    }
}
