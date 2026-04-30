package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * MessageItemType identifies the content type of a message item.
 */
public enum MessageItemType {
    NONE(0),
    TEXT(1),
    IMAGE(2),
    VOICE(3),
    FILE(4),
    VIDEO(5);

    private final int value;

    MessageItemType(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    public static MessageItemType fromValue(int value) {
        for (MessageItemType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return NONE;
    }
}
