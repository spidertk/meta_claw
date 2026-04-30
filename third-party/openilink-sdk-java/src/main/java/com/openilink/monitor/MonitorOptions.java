package com.openilink.monitor;

import lombok.Builder;
import lombok.Getter;

import java.util.function.Consumer;

/**
 * MonitorOptions configures the long-poll monitor loop.
 */
@Getter
@Builder
public class MonitorOptions {

    /** The get_updates_buf to resume from (empty for fresh start). */
    @Builder.Default
    private String initialBuf = "";

    /** Called whenever a new sync cursor is received. Persist this value to resume polling. */
    private Consumer<String> onBufUpdate;

    /** Called on non-fatal poll errors. */
    private Consumer<Exception> onError;

    /** Called when the server returns errcode -14. */
    private Runnable onSessionExpired;
}
