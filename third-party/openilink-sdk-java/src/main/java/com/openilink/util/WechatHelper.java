package com.openilink.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Weixin-specific helper methods.
 */
public final class WechatHelper {

    private static final SecureRandom RANDOM = new SecureRandom();

    private WechatHelper() {
    }

    /**
     * Generates a random X-WECHAT-UIN header value.
     */
    public static String randomWechatUIN() {
        int n = RANDOM.nextInt() & 0x7FFFFFFF;
        return Base64.getEncoder().encodeToString(String.valueOf(n).getBytes());
    }
}
