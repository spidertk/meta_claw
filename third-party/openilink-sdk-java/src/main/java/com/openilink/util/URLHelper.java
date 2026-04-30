package com.openilink.util;

/**
 * URL utility methods.
 */
public final class URLHelper {

    private URLHelper() {
    }

    /**
     * Ensures the URL ends with a trailing slash.
     */
    public static String ensureTrailingSlash(String url) {
        if (url == null || url.isEmpty()) {
            return "/";
        }
        return url.endsWith("/") ? url : url + "/";
    }

    /**
     * Joins a base URL with a path segment.
     */
    public static String joinPath(String base, String path) {
        String normalizedBase = ensureTrailingSlash(base);
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        return normalizedBase + normalizedPath;
    }
}
