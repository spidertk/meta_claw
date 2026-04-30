package com.openilink.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HTTPErrorTest {

    @Test
    void errorMessage_containsStatusAndBody() {
        HTTPError err = new HTTPError(404, "not found".getBytes());
        assertTrue(err.getMessage().contains("404"));
        assertTrue(err.getMessage().contains("not found"));
    }

    @Test
    void getters_returnCorrectValues() {
        byte[] body = "error".getBytes();
        HTTPError err = new HTTPError(500, body);
        assertEquals(500, err.getStatusCode());
        assertArrayEquals(body, err.getBody());
    }
}
