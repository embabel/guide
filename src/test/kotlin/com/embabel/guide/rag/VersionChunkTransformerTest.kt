package com.embabel.guide.rag

import com.embabel.agent.rag.ingestion.ChunkTransformationContext
import com.embabel.agent.rag.ingestion.ContentFetcher
import com.embabel.agent.rag.ingestion.FetchResult
import com.embabel.agent.rag.ingestion.TikaHierarchicalContentReader
import com.embabel.agent.rag.model.Chunk
import com.embabel.agent.rag.model.Section
import com.embabel.guide.ContentConfig
import com.embabel.guide.GuideProperties
import com.embabel.guide.VersionedContentConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.util.MimeType

class VersionChunkTransformerTest {

    private val hub = SiteConfig(
        sitemap = "https://hub.embabel.com/sitemap.xml",
        include = listOf("https://hub.embabel.com/**"),
    )

    private fun versionOf(uri: String?, versioned: VersionedContentConfig): Any? {
        val properties = GuideProperties(
            reloadContentOnStartup = false,
            defaultPersona = "adaptive",
            projectsPath = "projects",
            chunkerConfig = null,
            referencesFile = "references.yml",
            content = ContentConfig(versioned = versioned),
            toolPrefix = "",
            directories = null,
            toolGroups = emptySet(),
        )
        // A real document read from markdown, so it keeps the URL it was read from.
        val markdown = ContentFetcher { FetchResult("# Page\n\n## Part\n\nText.\n".toByteArray(), MimeType("text", "markdown")) }
        val document = TikaHierarchicalContentReader(markdown).parseUrl(uri ?: "https://example.com/page")
        val section = document.descendants().filterIsInstance<Section>().first()
        val context = ChunkTransformationContext(section, if (uri == null) null else document)
        return VersionChunkTransformer(properties).additionalMetadata(mock(Chunk::class.java), context)["version"]
    }

    @Test
    fun `a page of the docs' site is stamped with the docs version`() {
        val docs = VersionedContentConfig("1.5.4", hub)
        assertEquals("1.5.4", versionOf("https://hub.embabel.com/reference/flow", docs))
        assertEquals("1.5.4", versionOf("https://hub.embabel.com/", docs))
    }

    @Test
    fun `anything else is supplementary`() {
        val docs = VersionedContentConfig("1.5.4", hub)
        assertEquals("supplementary", versionOf("https://drivine.org/jvm/testing/", docs))
        assertEquals("supplementary", versionOf(null, docs))
    }

    @Test
    fun `without a docs version, everything is supplementary`() {
        assertEquals("supplementary", versionOf("https://hub.embabel.com/reference/flow", VersionedContentConfig("", hub)))
    }
}
