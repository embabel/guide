package com.embabel.guide.rag;

import java.time.Duration;
import java.util.List;

/**
 * Structured result from a full ingestion run (URLs + directories).
 * Failed items carry an {@link IngestionFailure} with a reason so operators
 * can diagnose problems from the summary banner alone.
 */
public record IngestionResult(
        List<String> loadedUrls,
        List<IngestionFailure> failedUrls,
        List<String> ingestedDirectories,
        List<IngestionFailure> failedDirectories,
        /** Per-document failures that occurred inside otherwise-successful directories. */
        List<IngestionFailure> failedDocuments,
        Duration elapsed
) {

    public int totalUrls() {
        return loadedUrls.size() + failedUrls.size();
    }

    public int totalDirectories() {
        return ingestedDirectories.size() + failedDirectories.size();
    }

    public boolean hasFailures() {
        return !failedUrls.isEmpty() || !failedDirectories.isEmpty() || !failedDocuments.isEmpty();
    }

    public int totalFailures() {
        return failedUrls.size() + failedDirectories.size() + failedDocuments.size();
    }
}
