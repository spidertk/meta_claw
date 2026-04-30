package com.openilink.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoContextTokenExceptionTest {

    @Test
    void defaultMessage() {
        NoContextTokenException ex = new NoContextTokenException();
        assertTrue(ex.getMessage().contains("no cached context token"));
    }

    @Test
    void messageWithUserId() {
        NoContextTokenException ex = new NoContextTokenException("user123");
        assertTrue(ex.getMessage().contains("user123"));
    }
}
