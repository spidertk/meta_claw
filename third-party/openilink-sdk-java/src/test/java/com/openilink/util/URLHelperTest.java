package com.openilink.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class URLHelperTest {

    @Test
    void ensureTrailingSlash_addsSlash() {
        assertEquals("https://example.com/", URLHelper.ensureTrailingSlash("https://example.com"));
    }

    @Test
    void ensureTrailingSlash_keepsExistingSlash() {
        assertEquals("https://example.com/", URLHelper.ensureTrailingSlash("https://example.com/"));
    }

    @Test
    void ensureTrailingSlash_emptyString() {
        assertEquals("/", URLHelper.ensureTrailingSlash(""));
    }

    @Test
    void ensureTrailingSlash_nullString() {
        assertEquals("/", URLHelper.ensureTrailingSlash(null));
    }

    @Test
    void joinPath_normalCase() {
        assertEquals("https://example.com/api/v1", URLHelper.joinPath("https://example.com", "api/v1"));
    }

    @Test
    void joinPath_baseWithTrailingSlash() {
        assertEquals("https://example.com/api/v1", URLHelper.joinPath("https://example.com/", "api/v1"));
    }

    @Test
    void joinPath_pathWithLeadingSlash() {
        assertEquals("https://example.com/api/v1", URLHelper.joinPath("https://example.com", "/api/v1"));
    }

    @Test
    void joinPath_bothSlashes() {
        assertEquals("https://example.com/api/v1", URLHelper.joinPath("https://example.com/", "/api/v1"));
    }
}
