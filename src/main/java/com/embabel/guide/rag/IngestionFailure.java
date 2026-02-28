package com.embabel.guide.rag;

/**
 * Captures the identity of a failed ingestion item together with
 * a human-readable reason so operators can diagnose problems without
 * digging through logs.
 *
 * @param source the URL or directory path that failed
 * @param reason short description of what went wrong
 */
public record IngestionFailure(
        String source,
        String reason
) {
    /**
     * Build from an exception, using its message (or class name as fallback).
     */
    public static IngestionFailure fromException(String source, Throwable t) {
        String reason = t.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = t.getClass().getSimpleName();
        }
        return new IngestionFailure(source, reason);
    }
}
