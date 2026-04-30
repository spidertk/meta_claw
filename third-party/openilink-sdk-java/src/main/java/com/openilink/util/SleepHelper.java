package com.openilink.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Concurrency utility methods.
 */
public final class SleepHelper {

    private SleepHelper() {
    }

    /**
     * Sleeps for the specified duration, but can be interrupted early.
     * Returns true if interrupted, false if the sleep completed normally.
     */
    public static boolean sleepInterruptibly(long millis) {
        try {
            Thread.sleep(millis);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }
}
