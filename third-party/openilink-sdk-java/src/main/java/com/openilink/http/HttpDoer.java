package com.openilink.http;

import java.io.IOException;
import java.util.Map;

/**
 * HttpDoer executes HTTP requests. Allows custom transport layer and testing.
 */
public interface HttpDoer {

    /**
     * Performs an HTTP POST request.
     *
     * @param url     the full URL
     * @param body    the request body bytes
     * @param headers request headers
     * @param timeoutMs timeout in milliseconds
     * @return the response body bytes
     * @throws IOException on transport error
     */
    byte[] doPost(String url, byte[] body, Map<String, String> headers, long timeoutMs) throws IOException;

    /**
     * Performs an HTTP GET request.
     *
     * @param url     the full URL
     * @param headers request headers
     * @param timeoutMs timeout in milliseconds
     * @return the response body bytes
     * @throws IOException on transport error
     */
    byte[] doGet(String url, Map<String, String> headers, long timeoutMs) throws IOException;
}
