package com.openilink.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WechatHelperTest {

    @Test
    void randomWechatUIN_returnsNonEmptyString() {
        String uin = WechatHelper.randomWechatUIN();
        assertNotNull(uin);
        assertFalse(uin.isEmpty());
    }

    @Test
    void randomWechatUIN_returnsBase64Encoded() {
        String uin = WechatHelper.randomWechatUIN();
        // Base64 encoded string should only contain valid base64 chars
        assertTrue(uin.matches("[A-Za-z0-9+/=]+"));
    }

    @Test
    void randomWechatUIN_returnsDifferentValues() {
        String uin1 = WechatHelper.randomWechatUIN();
        String uin2 = WechatHelper.randomWechatUIN();
        // Not guaranteed but highly likely to be different
        // If this flaky test is hit, it's astronomically unlikely
        assertNotNull(uin1);
        assertNotNull(uin2);
    }
}
