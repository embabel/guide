package com.embabel.guide;

import com.embabel.agent.rag.ingestion.ContentChunker;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.util.StringTransformer;
import jakarta.validation.constraints.NotBlank;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Configuration properties for the Guide application.
 *
 * @param reloadContentOnStartup whether to reload RAG content on startup
 * @param defaultPersona         name of the default persona to use
 * @param chatLlm                LLM options for chat
 * @param projectsPath           path to projects root: absolute, or relative to the process working directory (user.dir)
 * @param chatLlm                LLM options for RAG chat (beefy model)
 * @param fastLlm                LLM options for classification and quick responses (lightweight model)
 * @param projectsPath           path under user's home directory where projects are created
 * @param chunkerConfig          chunker configuration for RAG ingestion
 * @param referencesFile         YML files containing LLM references such as GitHub repositories and classpath info
 * @param urls                   list of URLs to ingest--for example, documentation and blogs
 * @param directories            optional list of local directory paths to ingest (full tree); resolved like projectsPath
 * @param toolGroups             toolGroups, such as "web", that are allowed
 */
@Validated
@ConfigurationProperties(prefix = "guide")
public record GuideProperties(
        boolean reloadContentOnStartup,
        @NotBlank(message = "defaultPersona must not be blank")
        String defaultPersona,
        LlmOptions chatLlm,
        LlmOptions fastLlm,
        @NotNull
        @NotBlank(message = "projectsPath must not be blank")
        String projectsPath,
        @NestedConfigurationProperty ContentChunker.Config chunkerConfig,
        @DefaultValue("references.yml")
        @NotBlank(message = "referencesFile must not be blank")
        String referencesFile,
        List<String> urls,
        @DefaultValue("")
        String toolPrefix,
        List<String> directories,
        Set<String> toolGroups,
        LlmOptions narratorLlm
) {

    public StringTransformer toolNamingStrategy() {
        return name -> toolPrefix + name;
    }

    /**
     * Resolves the projects path: if path starts with ~/, expands to user.home; if absolute, uses as-is;
     * otherwise resolves relative to user.dir.
     *
     * @return the absolute path to the projects root directory
     */
    public String projectRootPath() {
        return resolvePath(projectsPath);
    }

    /**
     * Resolves a path: ~/... to user.home, absolute as-is, else relative to user.dir.
     */
    public String resolvePath(String path) {
        return resolvePath(path, System.getProperty("user.home"), System.getProperty("user.dir"));
    }

    /**
     * Resolves a path with explicit home and cwd; used for testing.
     *
     * @param path     path to resolve (may be ~/..., absolute, or relative)
     * @param userHome value for user.home
     * @param userDir  value for user.dir (working directory)
     * @return resolved absolute path, or path if null/blank
     */
    static String resolvePath(String path, String userHome, String userDir) {
        if (path == null || path.isBlank()) {
            return path;
        }
        String expanded = path.strip();
        if (expanded.startsWith("~/") || "~".equals(expanded)) {
            expanded = expanded.length() == 1 ? userHome : Path.of(userHome, expanded.substring(2)).normalize().toString();
        }
        Path p = Path.of(expanded);
        if (p.isAbsolute()) {
            return p.normalize().toAbsolutePath().toString();
        }
        return Path.of(userDir, expanded).normalize().toAbsolutePath().toString();
    }
}