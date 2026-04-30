package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * MessageState represents the generation state of a message.
 */
public enum MessageState {
    NEW(0),
    GENERATING(1),
    FINISH(2);

    private final int value;

    MessageState(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    public static MessageState fromValue(int value) {
        for (MessageState state : values()) {
            if (state.value == value) {
                return state;
            }
        }
        return NEW;
    }
}
