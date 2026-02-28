package com.embabel.guide.rag;

import com.embabel.agent.rag.store.ContentElementRepositoryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs ingestion on startup when {@code guide.reload-content-on-startup} is true.
 * Prints a structured summary to stdout so the shell script (or human) can see
 * exactly what was loaded without parsing log files.
 */
@Component
@ConditionalOnProperty(name = "guide.reload-content-on-startup", havingValue = "true")
public class IngestionRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(IngestionRunner.class);

    private final DataManager dataManager;

    @Value("${server.port:8080}")
    private int serverPort;

    public IngestionRunner(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("IngestionRunner: starting ingestion (reload-content-on-startup=true)");

        var result = dataManager.loadReferences();

        var stats = dataManager.getStats();
        printSummary(result, stats);
    }

    private void printSummary(IngestionResult result, ContentElementRepositoryInfo stats) {
        var sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔══════════════════════════════════════════════════╗\n");
        sb.append("║           INGESTION COMPLETE                    ║\n");
        sb.append("╚══════════════════════════════════════════════════╝\n");
        sb.append("\n");

        sb.append("  Time: ").append(formatDuration(result.elapsed())).append("\n\n");

        sb.append("  ── URLs (").append(result.loadedUrls().size()).append("/")
                .append(result.totalUrls()).append(" loaded) ──\n");
        if (!result.loadedUrls().isEmpty()) {
            sb.append("    Loaded:\n");
            result.loadedUrls().forEach(u -> sb.append("      ✓ ").append(u).append("\n"));
        }
        if (!result.failedUrls().isEmpty()) {
            sb.append("    Failed:\n");
            result.failedUrls().forEach(f ->
                    sb.append("      ✗ ").append(f.source()).append("\n")
                      .append("        reason: ").append(f.reason()).append("\n"));
        }
        sb.append("\n");

        if (result.totalDirectories() > 0) {
            sb.append("  ── Directories (").append(result.ingestedDirectories().size()).append("/")
                    .append(result.totalDirectories()).append(" ingested) ──\n");
            if (!result.ingestedDirectories().isEmpty()) {
                sb.append("    Ingested:\n");
                result.ingestedDirectories().forEach(d -> sb.append("      ✓ ").append(d).append("\n"));
            }
            if (!result.failedDirectories().isEmpty()) {
                sb.append("    Failed:\n");
                result.failedDirectories().forEach(f ->
                        sb.append("      ✗ ").append(f.source()).append("\n")
                          .append("        reason: ").append(f.reason()).append("\n"));
            }
        } else {
            sb.append("  ── Directories: none configured ──\n");
        }

        if (!result.failedDocuments().isEmpty()) {
            sb.append("\n  ── Document Failures (").append(result.failedDocuments().size()).append(") ──\n");
            result.failedDocuments().forEach(f ->
                    sb.append("      ✗ ").append(f.source()).append("\n")
                      .append("        reason: ").append(f.reason()).append("\n"));
        }

        sb.append("\n");
        sb.append("  ── RAG Store ──\n");
        sb.append("    Documents: ").append(stats.getDocumentCount()).append("\n");
        sb.append("    Chunks:    ").append(stats.getChunkCount()).append("\n");
        sb.append("    Elements:  ").append(stats.getContentElementCount()).append("\n");
        sb.append("\n");

        sb.append("  Guide is running on port ").append(serverPort).append("\n");
        sb.append("  MCP endpoint: http://localhost:").append(serverPort).append("/sse\n");
        sb.append("\n");

        // Print to stdout so it's visible even without log-level config
        System.out.println(sb);
        logger.info("Ingestion summary printed to stdout");
    }

    private static String formatDuration(java.time.Duration d) {
        long totalSec = d.getSeconds();
        if (totalSec < 60) {
            return totalSec + "s";
        }
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return min + "m " + sec + "s";
    }
}
