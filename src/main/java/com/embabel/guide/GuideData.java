package com.embabel.guide;

import com.embabel.agent.api.common.LlmReference;
import com.embabel.agent.rag.WritableContentElementRepository;
import com.embabel.agent.rag.ingestion.DirectoryParsingResult;
import com.embabel.agent.rag.ingestion.HierarchicalContentReader;
import com.embabel.agent.rag.tools.RagOptions;
import com.embabel.agent.tools.file.FileTools;
import com.embabel.coding.tools.api.ApiReference;
import com.embabel.coding.tools.git.RepositoryReferenceProvider;
import com.embabel.coding.tools.jvm.ClassGraphApiReferenceExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/**
 * Exposes the guide configuration and the loaded references
 */
@Service
public class GuideData {

    private final Logger logger = LoggerFactory.getLogger(GuideData.class);
    private final GuideConfig guideConfig;
    private final List<LlmReference> references = new LinkedList<>();
    private final WritableContentElementRepository store;
    private final PlatformTransactionManager platformTransactionManager;

    public GuideData(
            WritableContentElementRepository store,
            GuideConfig guideConfig,
            PlatformTransactionManager platformTransactionManager) {
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

        try {
            var examplesReference = RepositoryReferenceProvider.create()
                    .cloneRepository("https://github.com/embabel/embabel-agent-examples.git");
            references.add(examplesReference);
        } catch (Throwable t) {
            // Allows working offline
            logger.warn("Failed to load Github");
        }
    }

    @NonNull
    public GuideConfig guideConfig() {
        return guideConfig;
    }

    @NonNull
    public List<LlmReference> references() {
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
            var directoryParsingResult = new HierarchicalContentReader()
                    .parseFromDirectory(ft);
            for (var root : directoryParsingResult.getContentRoots()) {
                logger.info("Parsed root: {} with {} descendants", root.getTitle(), root.descendants().size());
                store.writeContent(root);
            }
            return directoryParsingResult;
        });
    }

    public RagOptions ragOptions() {
        return new RagOptions()
                .withSimilarityThreshold(guideConfig.similarityThreshold())
                .withTopK(guideConfig.topK());
    }

    public Map<String, Object> templateModel(Map<String, Object> extras) {
        var n = new HashMap<>(extras);
        n.put("persona", guideConfig.persona());
        return n;
    }

}
