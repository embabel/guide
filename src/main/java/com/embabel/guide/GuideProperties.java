package com.embabel.guide;

import com.embabel.agent.rag.ingestion.ContentChunker;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.util.StringTransformer;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.List;

/**
 * Configuration properties for the Guide application.
 *
 * @param defaultPersona      name of the default persona to use
 * @param topK                RAG top K results to retrieve
 * @param similarityThreshold RAG similarity threshold
 * @param codingLlm           LLM options for coding tasks
 * @param chatLlm             LLM options for chat
 * @param projectsPath        path under user's home directory where projects are created
 * @param chunkerConfig       chunker configuration for RAG ingestion
 * @param referencesFile      YML files containing LLM references such as GitHub repositories and classpath info
 * @param urls                list of URLs to ingest--for example, documentation and blogs
 */
@Validated
@ConfigurationProperties(prefix = "guide")
public record GuideProperties(
        @NotBlank(message = "defaultPersona must not be blank")
        String defaultPersona,
        @DefaultValue("10")
        @Positive(message = "topK must be greater than 0")
        int topK,
        @DefaultValue("0.7")
        @DecimalMin(value = "0.0", message = "similarityThreshold must be between 0.0 and 1.0")
        @DecimalMax(value = "1.0", message = "similarityThreshold must be between 0.0 and 1.0")
        double similarityThreshold,
        LlmOptions codingLlm,
        LlmOptions chatLlm,
        @NotNull
        @NotBlank(message = "projectsPath must not be blank")
        String projectsPath,
        @NestedConfigurationProperty ContentChunker.DefaultConfig chunkerConfig,
        @DefaultValue("references.yml")
        @NotBlank(message = "referencesFile must not be blank")
        String referencesFile,
        List<String> urls,
        @DefaultValue("")
        String toolPrefix
) {

    public StringTransformer toolNamingStrategy() {
        return name -> toolPrefix + name;
    }

    /**
     * Returns the root path for projects, combining the user's home directory with the specified projects path.
     *
     * @return the full path to the projects root directory
     */
    public String projectRootPath() {
        return Path.of(System.getProperty("user.home"), projectsPath).toString();
    }
}