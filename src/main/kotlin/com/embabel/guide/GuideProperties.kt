package com.embabel.guide

import com.embabel.agent.rag.ingestion.ContentChunker
import com.embabel.agent.rag.ingestion.FetchRoute
import com.embabel.common.util.StringTransformer
import com.embabel.guide.rag.SiteConfig
import com.embabel.hub.integrations.LlmProvider
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.validation.annotation.Validated
import java.nio.file.Path

/**
 * Versioned content source configuration.
 * URLs are built from baseUrl + version + "/".
 */
data class VersionedContentConfig(
    val baseUrl: String,
    val versions: List<String> = emptyList(),
) {
    fun urls(): List<String> = versions.map { version -> "${baseUrl}$version/" }
}

/**
 * Content source configuration, separating versioned docs from supplementary material.
 *
 * @param sites sites ingested page by page from the markdown beside each page, each under the page's
 *              URL. They are supplementary material, searched whatever the docs version.
 */
data class ContentConfig(
    @NestedConfigurationProperty val versioned: VersionedContentConfig,
    val supplementary: List<String> = emptyList(),
    val sites: List<SiteConfig> = emptyList(),
) {
    fun allUrls(): List<String> = versioned.urls() + supplementary

    val activeVersion: String? get() = versioned.versions.firstOrNull()
}

/**
 * Configuration properties for the Guide application.
 *
 * @param reloadContentOnStartup whether to reload RAG content on startup
 * @param defaultPersona         name of the default persona to use
 * @param defaultProvider        which LLM provider to use for server-side defaults; auto-detected from env vars if not set
 * @param projectsPath           path to projects root: absolute, or relative to the process working directory (user.dir)
 * @param chunkerConfig          chunker configuration for RAG ingestion
 * @param referencesFile         YML files containing LLM references such as GitHub repositories and classpath info
 * @param content                content source configuration (versioned docs + supplementary)
 * @param directories            optional list of local directory paths to ingest (full tree); resolved like projectsPath
 * @param toolGroups             toolGroups, such as "web", that are allowed
 */
@Validated
@ConfigurationProperties(prefix = "guide")
data class GuideProperties(
    val reloadContentOnStartup: Boolean,
    @field:NotBlank(message = "defaultPersona must not be blank")
    val defaultPersona: String,
    val defaultProvider: LlmProvider? = null,
    @field:NotBlank(message = "projectsPath must not be blank")
    val projectsPath: String,
    @NestedConfigurationProperty val chunkerConfig: ContentChunker.Config?,
    @DefaultValue("references.yml")
    @field:NotBlank(message = "referencesFile must not be blank")
    val referencesFile: String,
    @NestedConfigurationProperty val content: ContentConfig,
    @DefaultValue("")
    val toolPrefix: String,
    val directories: List<String>?,
    val toolGroups: Set<String>,
    val fetchRoutes: List<FetchRoute> = emptyList(),
) {

    /** All URLs to ingest (versioned + supplementary). */
    val urls: List<String> get() = content.allUrls()

    fun toolNamingStrategy(): StringTransformer = StringTransformer { name -> toolPrefix + name }

    /**
     * Resolves the projects path: if path starts with ~/, expands to user.home; if absolute, uses as-is;
     * otherwise resolves relative to user.dir.
     *
     * @return the absolute path to the projects root directory
     */
    fun projectRootPath(): String = resolvePath(projectsPath)

    /**
     * Resolves a path: ~/... to user.home, absolute as-is, else relative to user.dir.
     */
    fun resolvePath(path: String): String =
        resolvePath(path, System.getProperty("user.home"), System.getProperty("user.dir"))!!

    companion object {
        /**
         * Resolves a path with explicit home and cwd; used for testing.
         *
         * @param path     path to resolve (may be ~/..., absolute, or relative)
         * @param userHome value for user.home
         * @param userDir  value for user.dir (working directory)
         * @return resolved absolute path, or path if null/blank
         */
        @JvmStatic
        fun resolvePath(path: String?, userHome: String, userDir: String): String? {
            if (path == null || path.isBlank()) {
                return path
            }
            var expanded = path.trim()
            if (expanded.startsWith("~/") || expanded == "~") {
                expanded = if (expanded.length == 1) userHome
                else Path.of(userHome, expanded.substring(2)).normalize().toString()
            }
            val p = Path.of(expanded)
            return if (p.isAbsolute) {
                p.normalize().toAbsolutePath().toString()
            } else {
                Path.of(userDir, expanded).normalize().toAbsolutePath().toString()
            }
        }
    }
}
