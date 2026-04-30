package com.openilink.exception;

/**
 * Base exception for all iLink SDK errors.
 */
public class ILinkException extends RuntimeException {

    public ILinkException(String message) {
        super(message);
    }

    public ILinkException(String message, Throwable cause) {
        super(message, cause);
    }
}
