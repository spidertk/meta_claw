package com.openilink.util;

import com.openilink.model.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class MessageHelperTest {

    @Test
    void extractText_returnsText() {
        WeixinMessage msg = WeixinMessage.builder()
                .itemList(Collections.singletonList(
                        MessageItem.builder()
                                .type(MessageItemType.TEXT)
                                .textItem(TextItem.builder().text("hello").build())
                                .build()
                ))
                .build();

        assertEquals("hello", MessageHelper.extractText(msg));
    }

    @Test
    void extractText_returnsFirstText() {
        WeixinMessage msg = WeixinMessage.builder()
                .itemList(Arrays.asList(
                        MessageItem.builder()
                                .type(MessageItemType.TEXT)
                                .textItem(TextItem.builder().text("first").build())
                                .build(),
                        MessageItem.builder()
                                .type(MessageItemType.TEXT)
                                .textItem(TextItem.builder().text("second").build())
                                .build()
                ))
                .build();

        assertEquals("first", MessageHelper.extractText(msg));
    }

    @Test
    void extractText_noTextItem_returnsEmpty() {
        WeixinMessage msg = WeixinMessage.builder()
                .itemList(Collections.singletonList(
                        MessageItem.builder()
                                .type(MessageItemType.IMAGE)
                                .imageItem(ImageItem.builder().url("http://img.com/1.jpg").build())
                                .build()
                ))
                .build();

        assertEquals("", MessageHelper.extractText(msg));
    }

    @Test
    void extractText_nullItemList_returnsEmpty() {
        WeixinMessage msg = WeixinMessage.builder().build();
        assertEquals("", MessageHelper.extractText(msg));
    }

    @Test
    void extractText_nullMessage_returnsEmpty() {
        assertEquals("", MessageHelper.extractText(null));
    }

    @Test
    void extractText_textItemWithNullTextItem_returnsEmpty() {
        WeixinMessage msg = WeixinMessage.builder()
                .itemList(Collections.singletonList(
                        MessageItem.builder()
                                .type(MessageItemType.TEXT)
                                .textItem(null)
                                .build()
                ))
                .build();

        assertEquals("", MessageHelper.extractText(msg));
    }
}
