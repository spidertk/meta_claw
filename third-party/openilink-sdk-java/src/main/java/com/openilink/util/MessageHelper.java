package com.openilink.util;

import com.openilink.model.MessageItem;
import com.openilink.model.MessageItemType;
import com.openilink.model.WeixinMessage;

import java.util.List;

/**
 * Message utility methods.
 */
public final class MessageHelper {

    private MessageHelper() {
    }

    /**
     * Returns the first text body from a message's item list.
     * Returns an empty string if no text item is found.
     */
    public static String extractText(WeixinMessage msg) {
        if (msg == null) {
            return "";
        }
        List<MessageItem> items = msg.getItemList();
        if (items == null) {
            return "";
        }
        for (MessageItem item : items) {
            if (item.getType() == MessageItemType.TEXT && item.getTextItem() != null) {
                return item.getTextItem().getText();
            }
        }
        return "";
    }
}
