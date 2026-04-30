package com.openilink.exception;

/**
 * Thrown when Push is called but no cached context token exists for the target user.
 */
public class NoContextTokenException extends ILinkException {

    public NoContextTokenException() {
        super("ilink: no cached context token; user must send a message first");
    }

    public NoContextTokenException(String userId) {
        super("ilink: no cached context token for user " + userId + "; user must send a message first");
    }
}
