package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * TypingStatus represents the typing indicator state.
 */
public enum TypingStatus {
    TYPING(1),
    CANCEL_TYPING(2);

    private final int value;

    TypingStatus(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    public static TypingStatus fromValue(int value) {
        for (TypingStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        return TYPING;
    }
}
