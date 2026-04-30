package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * MessageType distinguishes user messages from bot messages.
 */
public enum MessageType {
    NONE(0),
    USER(1),
    BOT(2);

    private final int value;

    MessageType(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    public static MessageType fromValue(int value) {
        for (MessageType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return NONE;
    }
}
