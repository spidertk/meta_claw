package com.openilink.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class APIErrorTest {

    @Test
    void errorMessage_containsAllFields() {
        APIError err = new APIError(1, -14, "session expired");
        assertTrue(err.getMessage().contains("ret=1"));
        assertTrue(err.getMessage().contains("errcode=-14"));
        assertTrue(err.getMessage().contains("session expired"));
    }

    @Test
    void isSessionExpired_errCodeMinus14() {
        APIError err = new APIError(0, -14, "expired");
        assertTrue(err.isSessionExpired());
    }

    @Test
    void isSessionExpired_retMinus14() {
        APIError err = new APIError(-14, 0, "expired");
        assertTrue(err.isSessionExpired());
    }

    @Test
    void isSessionExpired_notExpired() {
        APIError err = new APIError(1, -1, "other error");
        assertFalse(err.isSessionExpired());
    }

    @Test
    void getters_returnCorrectValues() {
        APIError err = new APIError(1, 2, "test");
        assertEquals(1, err.getRet());
        assertEquals(2, err.getErrCode());
        assertEquals("test", err.getErrMsg());
    }
}
