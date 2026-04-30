package com.openilink.exception;

import lombok.Getter;

/**
 * HTTPError represents a non-2xx HTTP response from the server.
 */
@Getter
public class HTTPError extends ILinkException {

    private final int statusCode;
    private final byte[] body;

    public HTTPError(int statusCode, byte[] body) {
        super(String.format("ilink: http %d: %s", statusCode, new String(body)));
        this.statusCode = statusCode;
        this.body = body;
    }
}
