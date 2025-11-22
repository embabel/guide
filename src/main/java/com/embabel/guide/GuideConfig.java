package com.embabel.guide;

import com.embabel.agent.rag.ingestion.ContentChunker;
import com.embabel.agent.rag.service.*;
import com.embabel.agent.rag.tools.RagOptions;
import com.embabel.common.ai.model.LlmOptions;
import jakarta.validation.constraints.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.NonNull;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "guide")
public record GuideConfig(
        @NotBlank(message = "defaultPersona must not be blank")
        String defaultPersona,
        @DefaultValue("10")
        @Positive(message = "topK must be greater than 0")
        int topK,
        @DefaultValue("0.7")
        @DecimalMin(value = "0.0", message = "similarityThreshold must be between 0.0 and 1.0")
        @DecimalMax(value = "1.0", message = "similarityThreshold must be between 0.0 and 1.0")
        double similarityThreshold,
        HyDE hyDE,
        LlmOptions codingLlm,
        LlmOptions chatLlm,
        DesiredMaxLatency desiredMaxLatency,
        @NotNull
        @NotBlank(message = "projectsPath must not be blank")
        String projectsPath,
        @DefaultValue("8000")
        @Positive(message = "maxChunkSize must be greater than 0")
        int maxChunkSize,
        @DefaultValue("200")
        @PositiveOrZero(message = "overlapSize must be non-negative")
        int overlapSize,
        @DefaultValue("false")
        boolean includeSectionTitleInChunk,
        @DefaultValue("references.yml")
        @NotBlank(message = "referencesFile must not be blank")
        String referencesFile,
        List<String> urls
) implements ContentChunker.Config {

    /**
     * Compact constructor for additional validation
     */
    public GuideConfig {
        if (maxChunkSize <= overlapSize) {
            throw new IllegalArgumentException(
                    "maxChunkSize (" + maxChunkSize + ") must be greater than overlapSize (" + overlapSize + ")"
            );
        }
    }

    @Override
    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    @Override
    public int getOverlapSize() {
        return overlapSize;
    }

    @Override
    public boolean getIncludeSectionTitleInChunk() {
        return includeSectionTitleInChunk;
    }

    /**
     * Returns the root path for projects, combining the user's home directory with the specified projects path.
     *
     * @return the full path to the projects root directory
     */
    public String projectRootPath() {
        return Path.of(System.getProperty("user.home"), projectsPath).toString();
    }

    @NonNull
    public RagOptions ragOptions(RagService ragService) {
        return new RagOptions(ragService)
                .withSimilarityThreshold(similarityThreshold())
                .withTopK(topK())
                .withContentElementSearch(ContentElementSearch.CHUNKS_ONLY)
                .withEntitySearch(new EntitySearch(Set.of(
                        "Concept", "Example"
                ), false))
                .withHint(hyDE())
                .withHint(desiredMaxLatency);
    }

}