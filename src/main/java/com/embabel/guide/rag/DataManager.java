package com.embabel.guide.rag;

import com.embabel.agent.api.identity.User;
import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.api.reference.LlmReferenceProviders;
import com.embabel.agent.rag.ingestion.*;
import com.embabel.agent.rag.ingestion.policy.UrlSpecificContentRefreshPolicy;
import com.embabel.agent.rag.model.NavigableDocument;
import com.embabel.agent.rag.store.ChunkingContentElementRepository;
import com.embabel.agent.rag.store.ContentElementRepositoryInfo;
import com.embabel.agent.tools.file.FileTools;
import com.embabel.guide.GuideProperties;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exposes references and RAG configuration.
 * Depends on {@link ChunkingContentElementRepository} from the rag-core library,
 * so any backend implementing that interface (e.g. DrivineStore for Neo4j) can be
 * plugged in without changes here.
 */
@Service
public class DataManager {

    private final Logger logger = LoggerFactory.getLogger(DataManager.class);
    private final GuideProperties guideProperties;
    private final List<LlmReference> references;
    private final ChunkingContentElementRepository store;

    private final HierarchicalContentReader hierarchicalContentReader = new TikaHierarchicalContentReader();

    // Refresh only snapshots
    private final ContentRefreshPolicy contentRefreshPolicy = UrlSpecificContentRefreshPolicy.containingAny(
            "-SNAPSHOT"
    );

    public DataManager(
            ChunkingContentElementRepository store,
            GuideProperties guideProperties
    ) {
        this.store = store;
        this.guideProperties = guideProperties;
        this.references = LlmReferenceProviders.fromYmlFile(guideProperties.referencesFile());
        store.provision();
        // Ingestion on startup is now handled by IngestionRunner (ApplicationRunner)
        // which is activated by guide.reload-content-on-startup=true
    }

    public ContentElementRepositoryInfo getStats() {
        return store.info();
    }

    /** Convenience for tests that cannot reference Stats (e.g. Kotlin). */
    public int getDocumentCount() {
        return store.info().getDocumentCount();
    }

    /** Convenience for tests that cannot reference Stats (e.g. Kotlin). */
    public int getChunkCount() {
        return store.info().getChunkCount();
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
     * Read all files under this directory on this local machine.
     * Each document is written individually so a single failure does not
     * prevent the remaining documents from being ingested.
     *
     * @param dir             absolute path
     * @param failedDocuments collector for per-document failures (mutated)
     * @return the parsing result (may still be useful even when some documents failed)
     */
    public DirectoryParsingResult ingestDirectory(String dir, List<IngestionFailure> failedDocuments) {
        var ft = FileTools.readOnly(dir);
        var directoryParsingResult = new TikaHierarchicalContentReader()
                .parseFromDirectory(ft, new DirectoryParsingConfig());
        for (var root : directoryParsingResult.getContentRoots()) {
            String docTitle = "unknown";
            try {
                var doc = (NavigableDocument) root;
                docTitle = doc.getTitle();
                logger.info("Parsed root: {} with {} descendants", docTitle,
                        Iterables.size(doc.descendants()));
                store.writeAndChunkDocument(doc);
            } catch (Throwable t) {
                logger.error("Failed to write document '{}' from directory {}: {}",
                        docTitle, dir, t.getMessage(), t);
                failedDocuments.add(IngestionFailure.fromException(
                        dir + " -> " + docTitle, t));
            }
        }
        return directoryParsingResult;
    }

    /**
     * Ingest the page at the given URL
     *
     * @param url the URL to ingest
     */
    public void ingestPage(String url) {
        var root = contentRefreshPolicy.ingestUriIfNeeded(store, hierarchicalContentReader, url);
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
     * Load all referenced URLs and directories from configuration.
     * Each item is ingested independently -- a single failure never prevents
     * the remaining items from being processed.
     *
     * @return structured result with loaded/failed URLs and directories (with reasons)
     */
    public IngestionResult loadReferences() {
        var start = Instant.now();
        var loadedUrls = new ArrayList<String>();
        var failedUrls = new ArrayList<IngestionFailure>();
        var ingestedDirs = new ArrayList<String>();
        var failedDirs = new ArrayList<IngestionFailure>();
        var failedDocuments = new ArrayList<IngestionFailure>();

        for (var url : guideProperties.urls()) {
            try {
                logger.info("⏳ Loading URL: {}...", url);
                ingestPage(url);
                logger.info("✅ Loaded URL: {}", url);
                loadedUrls.add(url);
            } catch (Throwable t) {
                logger.error("❌ Failure loading URL {}: {}", url, t.getMessage(), t);
                failedUrls.add(IngestionFailure.fromException(url, t));
            }
        }
        logger.info("Loaded {}/{} URLs successfully ({} failed)",
                loadedUrls.size(), guideProperties.urls().size(), failedUrls.size());

        List<String> dirs = guideProperties.directories();
        if (dirs != null && !dirs.isEmpty()) {
            for (String dir : dirs) {
                try {
                    String absolutePath = guideProperties.resolvePath(dir);
                    logger.info("⏳ Ingesting directory: {}...", absolutePath);
                    ingestDirectory(absolutePath, failedDocuments);
                    logger.info("✅ Ingested directory: {}", absolutePath);
                    ingestedDirs.add(absolutePath);
                } catch (Throwable t) {
                    logger.error("❌ Failure ingesting directory {}: {}", dir, t.getMessage(), t);
                    failedDirs.add(IngestionFailure.fromException(dir, t));
                }
            }
            logger.info("Ingested {}/{} directories ({} dir failures, {} document failures)",
                    ingestedDirs.size(), dirs.size(), failedDirs.size(), failedDocuments.size());
        } else {
            logger.info("No directories configured for ingestion (guide.directories empty or not set)");
        }

        return new IngestionResult(loadedUrls, failedUrls, ingestedDirs, failedDirs,
                failedDocuments, Duration.between(start, Instant.now()));
    }

}
