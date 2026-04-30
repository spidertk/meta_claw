package com.openilink.exception;

import lombok.Getter;

/**
 * APIError represents an error response from the iLink API.
 */
@Getter
public class APIError extends ILinkException {

    private final int ret;
    private final int errCode;
    private final String errMsg;

    public APIError(int ret, int errCode, String errMsg) {
        super(String.format("ilink: api error ret=%d errcode=%d errmsg=%s", ret, errCode, errMsg));
        this.ret = ret;
        this.errCode = errCode;
        this.errMsg = errMsg;
    }

    /**
     * Reports whether the error indicates an expired session (errcode -14).
     */
    public boolean isSessionExpired() {
        return errCode == -14 || ret == -14;
    }
}
