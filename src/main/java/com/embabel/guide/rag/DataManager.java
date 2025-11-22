package com.embabel.guide.rag;

import com.embabel.agent.api.common.LlmReference;
import com.embabel.agent.api.common.reference.LlmReferenceProviders;
import com.embabel.agent.api.identity.User;
import com.embabel.agent.rag.ingestion.DirectoryParsingConfig;
import com.embabel.agent.rag.ingestion.DirectoryParsingResult;
import com.embabel.agent.rag.ingestion.NeverRefreshExistingDocumentContentPolicy;
import com.embabel.agent.rag.ingestion.TikaHierarchicalContentReader;
import com.embabel.agent.rag.service.RagService;
import com.embabel.agent.rag.store.ChunkingContentElementRepository;
import com.embabel.agent.tools.file.FileTools;
import com.embabel.guide.GuideProperties;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * Exposes references and RAG configuration
 */
@Service
public class DataManager {

    private final Logger logger = LoggerFactory.getLogger(DataManager.class);
    private final GuideProperties guideProperties;
    private final List<LlmReference> references;
    private final ChunkingContentElementRepository store;
    private final PlatformTransactionManager platformTransactionManager;
    private final RagService embabelContentRagService;

    public DataManager(
            ChunkingContentElementRepository store,
            GuideProperties guideProperties,
            PlatformTransactionManager platformTransactionManager,
            RagService embabelContentRagService) {
        this.store = store;
        this.guideProperties = guideProperties;
        this.platformTransactionManager = platformTransactionManager;
        this.references = LlmReferenceProviders.fromYmlFile(guideProperties.referencesFile());
        this.embabelContentRagService = embabelContentRagService;
    }

    @NonNull
    public RagService embabelContentRagService() {
        return embabelContentRagService;
    }

    @NonNull
    public List<LlmReference> referencesForUser(@Nullable User user) {
        return Collections.unmodifiableList(references);
    }

    public void provisionDatabase() {
        store.provision();
    }

    @Transactional(readOnly = true)
    public int count() {
        return store.count();
    }

    /**
     * Read all files under this directory on this local machine
     *
     * @param dir absolute path
     */
    public DirectoryParsingResult ingestDirectory(String dir) {
        store.provision();

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
        store.provision();

        var root = NeverRefreshExistingDocumentContentPolicy.INSTANCE
                .ingestUriIfNeeded(store, new TikaHierarchicalContentReader(), url);
        if (root != null) {
            logger.info("Ingested page: {} with {} descendants",
                    root.getTitle(),
                    Iterables.size(root.descendants())
            );
        } else {
            logger.info("Page at {} was already ingested, skipping", url);
        }
    }

}
