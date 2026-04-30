package com.openilink.auth;

/**
 * LoginCallbacks receives events during the QR login flow.
 */
public interface LoginCallbacks {

    /**
     * Called when a QR code URL is ready for the user to scan.
     */
    default void onQRCode(String qrCodeUrl) {
    }

    /**
     * Called once after the user scans the QR code.
     */
    default void onScanned() {
    }

    /**
     * Called when the QR code expires and a new one is fetched.
     */
    default void onExpired(int attempt, int maxAttempts) {
    }
}
