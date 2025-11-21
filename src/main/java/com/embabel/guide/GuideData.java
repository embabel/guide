package com.embabel.guide;

import com.embabel.agent.api.common.LlmReference;
import com.embabel.agent.identity.User;
import com.embabel.agent.rag.ingestion.DirectoryParsingConfig;
import com.embabel.agent.rag.ingestion.DirectoryParsingResult;
import com.embabel.agent.rag.ingestion.TikaHierarchicalContentReader;
import com.embabel.agent.rag.service.RagService;
import com.embabel.agent.rag.store.ChunkingContentElementRepository;
import com.embabel.agent.rag.tools.RagOptions;
import com.embabel.agent.tools.file.FileTools;
import com.embabel.coding.tools.api.ApiReference;
import com.embabel.coding.tools.git.RepositoryReferenceProvider;
import com.embabel.coding.tools.jvm.ClassGraphApiReferenceExtractor;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Exposes the guide configuration and the loaded references
 */
@Service
public class GuideData {

    private final Logger logger = LoggerFactory.getLogger(GuideData.class);
    private final GuideConfig guideConfig;
    private final List<LlmReference> references = new LinkedList<>();
    private final ChunkingContentElementRepository store;
    private final PlatformTransactionManager platformTransactionManager;
    private final RagService ragService;

    public GuideData(
            ChunkingContentElementRepository store,
            GuideConfig guideConfig,
            PlatformTransactionManager platformTransactionManager,
            RagService ragService) {
        this.store = store;
        this.guideConfig = guideConfig;
        this.platformTransactionManager = platformTransactionManager;
        var embabelAgentApiReference = new ApiReference(
                "Embabel Agent API: Core",
                new ClassGraphApiReferenceExtractor().fromProjectClasspath(
                        "embabel-agent",
                        Set.of("com.embabel.agent", "com.embabel.common"),
                        Set.of()),
                100);
        references.add(embabelAgentApiReference);
        this.ragService = ragService;

        // TODO this could be data driven
        addGithubReference("https://github.com/embabel/embabel-agent-examples.git", "Embabel examples repo");
        addGithubReference("https://github.com/embabel/embabel-agent.git", "Embabel agent implementation repo: Look to check code under embabel-agent-api");
    }

    private void addGithubReference(@NonNull String repoUrl, @NonNull String description) {
        try {
            var examplesReference = RepositoryReferenceProvider.create()
                    .cloneRepository(repoUrl, description);
            references.add(examplesReference);
            logger.info("Loaded Github repo {} for tool access", repoUrl);
        } catch (Throwable t) {
            // Allows working offline
            logger.warn("Failed to load Github repo {} for tool access", repoUrl);
        }
    }

    @NonNull
    public GuideConfig config() {
        return guideConfig;
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
     * Read all files under this directory
     *
     * @param dir absolute path
     */
    public DirectoryParsingResult ingestDirectory(String dir) {
        store.provision();

        var ft = FileTools.readOnly(dir);

        return new TransactionTemplate(platformTransactionManager).execute(ts -> {
            var directoryParsingResult = new TikaHierarchicalContentReader()
                    .parseFromDirectory(ft, new DirectoryParsingConfig());
            for (var root : directoryParsingResult.getContentRoots()) {
                logger.info("Parsed root: {} with {} descendants", root.getTitle(), Iterables.size(root.descendants()));
                store.writeAndChunkDocument(root);
            }
            return directoryParsingResult;
        });
    }

    public RagOptions ragOptions() {
        return new RagOptions(ragService)
                .withSimilarityThreshold(guideConfig.similarityThreshold())
                .withTopK(guideConfig.topK());
    }

}
