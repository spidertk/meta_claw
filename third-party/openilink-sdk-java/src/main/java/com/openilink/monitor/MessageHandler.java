package com.openilink.monitor;

import com.openilink.model.WeixinMessage;

/**
 * MessageHandler is called for each inbound message from getUpdates.
 */
@FunctionalInterface
public interface MessageHandler {

    /**
     * Handle an inbound message.
     *
     * @param msg the received message
     */
    void handle(WeixinMessage msg);
}
