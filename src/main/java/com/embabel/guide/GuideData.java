package com.embabel.guide;

import com.embabel.agent.api.common.AiBuilder;
import com.embabel.agent.api.common.LlmReference;
import com.embabel.agent.rag.WritableRagService;
import com.embabel.agent.rag.ingestion.HierarchicalContentReader;
import com.embabel.agent.rag.tools.RagOptions;
import com.embabel.agent.tools.file.FileTools;
import com.embabel.coding.tools.api.ApiReference;
import com.embabel.coding.tools.git.RepositoryReferenceProvider;
import com.embabel.coding.tools.jvm.ClassGraphApiReferenceExtractor;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exposes the guide configuration and the loaded references
 */
@Service
public class GuideData {

    public final GuideConfig guideConfig;

    public final List<LlmReference> references = new LinkedList<>();

    public GuideData(
            AiBuilder aiBuilder,
            WritableRagService ragService,
            GuideConfig guideConfig) {
        this.guideConfig = guideConfig;
        var dir = FileTools.readOnly(
                Path.of(System.getProperty("user.dir"), "data", "docs").toString()
        );

        var directoryParsingResult = new HierarchicalContentReader().parseFromDirectory(
                dir
        );
        for (var root : directoryParsingResult.getContentRoots()) {
            LoggerFactory.getLogger(GuideData.class).info("Parsed root: {} with {} descendants", root.getTitle(), root.descendants().size());
            ragService.writeContent(root);
        }

//        logger.info("Ingestion result: {}\nChatbot ready...", ingestionResult);
        var embabelAgentApiReference = new ApiReference(
                "Embabel Agent API: Core",
                new ClassGraphApiReferenceExtractor().fromProjectClasspath(
                        "embabel-agent",
                        Set.of("com.embabel.agent"),
                        Set.of()),
                100);
        var embabelCommmonApiReference = new ApiReference(
                "Embabel Agent API: Core",
                new ClassGraphApiReferenceExtractor().fromProjectClasspath(
                        "embabel-common",
                        Set.of("com.embabel"),
                        Set.of()),
                100);
        var examplesReference = RepositoryReferenceProvider.create()
                .cloneRepository("https://github.com/embabel/embabel-agent-examples.git");
        references.add(embabelAgentApiReference);
        references.add(embabelCommmonApiReference);
        references.add(examplesReference);
    }

    public RagOptions ragOptions() {
        return new RagOptions()
                .withSimilarityThreshold(guideConfig.similarityThreshold())
                .withTopK(guideConfig.topK());
    }

    public Map<String, Object> templateModel() {
        return Map.of("persona", guideConfig.persona());
    }
}
