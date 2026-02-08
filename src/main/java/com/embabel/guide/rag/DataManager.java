package com.embabel.guide.rag;

import com.embabel.agent.api.identity.User;
import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.api.reference.LlmReferenceProviders;
import com.embabel.agent.rag.ingestion.*;
import com.embabel.agent.rag.ingestion.policy.UrlSpecificContentRefreshPolicy;
import com.embabel.agent.rag.neo.drivine.DrivineStore;
import com.embabel.agent.tools.file.FileTools;
import com.embabel.guide.GuideProperties;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Exposes references and RAG configuration
 */
@Service
public class DataManager {

    public record Stats(
            int chunkCount,
            int documentCount,
            int contentElementCount) {
    }

    private final Logger logger = LoggerFactory.getLogger(DataManager.class);
    private final GuideProperties guideProperties;
    private final List<LlmReference> references;
    private final DrivineStore store;

    private final HierarchicalContentReader hierarchicalContentReader = new TikaHierarchicalContentReader();

    // Refresh only snapshots
    private final ContentRefreshPolicy contentRefreshPolicy = UrlSpecificContentRefreshPolicy.containingAny(
            "-SNAPSHOT"
    );

    public DataManager(
            DrivineStore store,
            GuideProperties guideProperties
    ) {
        this.store = store;
        this.guideProperties = guideProperties;
        this.references = LlmReferenceProviders.fromYmlFile(guideProperties.referencesFile());
        store.provision();
        if (guideProperties.reloadContentOnStartup()) {
            logger.info("Reloading RAG content on startup");
            loadReferences();
        }
    }

    public Stats getStats() {
        var info = store.info();
        return new Stats(info.getChunkCount(), info.getDocumentCount(), info.getContentElementCount());
    }

    @NonNull
    public List<LlmReference> referencesForAllUsers() {
        return Collections.unmodifiableList(references);
    }

    @NonNull
    public List<LlmReference> referencesForUser(@Nullable User user) {
        // Presently we have no user-specific references
        return referencesForAllUsers();
    }

    public void provisionDatabase() {
        store.provision();
    }

    /**
     * Read all files under this directory on this local machine
     *
     * @param dir absolute path
     */
    public DirectoryParsingResult ingestDirectory(String dir) {
        var ft = FileTools.readOnly(dir);
        var directoryParsingResult = new TikaHierarchicalContentReader()
                .parseFromDirectory(ft, new DirectoryParsingConfig());
        for (var root : directoryParsingResult.getContentRoots()) {
            logger.info("Parsed root: {} with {} descendants", root.getTitle(),
                    Iterables.size(root.descendants()));
            store.writeAndChunkDocument(root);
        }
        return directoryParsingResult;
    }

    /**
     * Ingest the page at the given URL
     *
     * @param url the URL to ingest
     */
    public void ingestPage(String url) {
        var root = contentRefreshPolicy
                .ingestUriIfNeeded(store, hierarchicalContentReader, url);
        if (root != null) {
            logger.info("Ingested page: {} with {} descendants",
                    root.getTitle(),
                    Iterables.size(root.descendants())
            );
        } else {
            logger.info("Page at {} was already ingested, skipping", url);
        }
    }

    /**
     * Load all referenced URLs from configuration
     */
    public void loadReferences() {
        int successCount = 0;
        int failureCount = 0;

        for (var url : guideProperties.urls()) {
            try {
                logger.info("⏳Loading URL: {}...", url);
                ingestPage(url);
                logger.info("✅ Loaded URL: {}", url);
                successCount++;

            } catch (Throwable t) {
                logger.error("❌ Failure loading URL {}: {}", url, t.getMessage(), t);
                failureCount++;
            }
        }
        logger.info("Loaded {}/{} URLs successfully ({} failed)",
                successCount, guideProperties.urls().size(), failureCount);
    }

}
