package com.embabel.guide;

import com.embabel.agent.api.common.LlmReference;
import com.embabel.agent.rag.WritableRagService;
import com.embabel.agent.rag.ingestion.DirectoryParsingResult;
import com.embabel.agent.rag.ingestion.HierarchicalContentReader;
import com.embabel.agent.rag.tools.RagOptions;
import com.embabel.agent.tools.file.FileTools;
import com.embabel.coding.tools.api.ApiReference;
import com.embabel.coding.tools.git.RepositoryReferenceProvider;
import com.embabel.coding.tools.jvm.ClassGraphApiReferenceExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/**
 * Exposes the guide configuration and the loaded references
 */
@Service
public class GuideData {

    private final Logger logger = LoggerFactory.getLogger(GuideData.class);

    public final GuideConfig guideConfig;

    public final List<LlmReference> references = new LinkedList<>();

    private final WritableRagService ragService;

    private final PlatformTransactionManager platformTransactionManager;

    public GuideData(
            WritableRagService ragService,
            GuideConfig guideConfig,
            PlatformTransactionManager platformTransactionManager) {
        this.ragService = ragService;
        this.guideConfig = guideConfig;
        this.platformTransactionManager = platformTransactionManager;
        var embabelAgentApiReference = new ApiReference(
                "Embabel Agent API: Core",
                new ClassGraphApiReferenceExtractor().fromProjectClasspath(
                        "embabel-agent",
                        Set.of("com.embabel.agent", "com.embabel.common"),
                        Set.of()),
                100);

        var examplesReference = RepositoryReferenceProvider.create()
                .cloneRepository("https://github.com/embabel/embabel-agent-examples.git");
        references.add(embabelAgentApiReference);
        references.add(examplesReference);
    }

    /**
     * Read all files under this directory
     *
     * @param dir absolute path
     *            =
     */
    public DirectoryParsingResult readContent(String dir) {
        ragService.provision();

        var ft = FileTools.readOnly(dir);

        return new TransactionTemplate(platformTransactionManager).execute(ts -> {
            var directoryParsingResult = new HierarchicalContentReader()
                    .parseFromDirectory(ft);
            for (var root : directoryParsingResult.getContentRoots()) {
                logger.info("Parsed root: {} with {} descendants", root.getTitle(), root.descendants().size());
                ragService.writeContent(root);
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
