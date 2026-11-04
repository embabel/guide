package com.embabel.guide.rag

import com.embabel.agent.rag.ingestion.ContentFetcher
import com.embabel.agent.rag.ingestion.FetchResult
import com.embabel.agent.rag.ingestion.RoutingContentFetcher
import com.embabel.agent.rag.ingestion.TikaHierarchicalContentReader
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI

class SiteSourceTest {

    /** Serves [pages] by URL, recording each URL fetched. */
    private class FakeFetcher(private val pages: Map<String, String>) : ContentFetcher {
        val fetched = mutableListOf<String>()
        override fun fetch(uri: URI): FetchResult {
            fetched += uri.toString()
            val body = pages[uri.toString()] ?: error("Not found: $uri")
            return FetchResult(body.toByteArray())
        }
    }

    private val drivine = SiteConfig(
        sitemap = "https://drivine.org/sitemap-index.xml",
        include = listOf("https://drivine.org/jvm/**"),
        exclude = listOf("https://drivine.org/jvm/license/"),
    )

    private fun urlset(vararg locs: String) =
        """<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">""" +
            locs.joinToString("") { "<url><loc>$it</loc></url>" } + "</urlset>"

    @Test
    fun `markdown beside a page is its path without the trailing slash, then md`() {
        assertEquals(
            URI("https://drivine.org/jvm/testing.md"),
            MarkdownSiblingFetcher.markdownOf(URI("https://drivine.org/jvm/testing/")),
        )
        assertEquals(
            URI("https://drivine.org/jvm.md"),
            MarkdownSiblingFetcher.markdownOf(URI("https://drivine.org/jvm/")),
        )
        assertEquals(
            URI("https://drivine.org/jvm/supported-engines/postgres.md"),
            MarkdownSiblingFetcher.markdownOf(URI("https://drivine.org/jvm/supported-engines/postgres")),
        )
    }

    @Test
    fun `markdown beside a page drops its query and fragment`() {
        assertEquals(
            URI("https://drivine.org/jvm/testing.md"),
            MarkdownSiblingFetcher.markdownOf(URI("https://drivine.org/jvm/testing/?x=1#setup")),
        )
    }

    @Test
    fun `a site's root page has no markdown beside it`() {
        assertThrows<IllegalArgumentException> { MarkdownSiblingFetcher.markdownOf(URI("https://drivine.org/")) }
    }

    @Test
    fun `sibling fetcher fetches the markdown and calls it markdown`() {
        val fake = FakeFetcher(mapOf("https://drivine.org/jvm/testing.md" to "# Testing"))
        val result = MarkdownSiblingFetcher(fake).fetch(URI("https://drivine.org/jvm/testing/"))

        assertEquals(listOf("https://drivine.org/jvm/testing.md"), fake.fetched)
        assertEquals("# Testing", result.content.decodeToString())
        assertEquals("text/markdown", "${result.contentType?.type}/${result.contentType?.subtype}")
    }

    @Test
    fun `a site ingests pages it includes and doesn't exclude`() {
        assertTrue(drivine.ingests("https://drivine.org/jvm/"))
        assertTrue(drivine.ingests("https://drivine.org/jvm/supported-engines/postgres/"))
        assertFalse(drivine.ingests("https://drivine.org/jvm/license/"))
        assertFalse(drivine.ingests("https://drivine.org/ts/quick-start/"))
        assertFalse(drivine.ingests("https://drivine.org/"))
    }

    @Test
    fun `site pages follow a sitemap index and keep the sitemap's order`() {
        val fake = FakeFetcher(
            mapOf(
                "https://drivine.org/sitemap-index.xml" to
                    """<?xml version="1.0"?><sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">""" +
                    "<sitemap><loc>https://drivine.org/sitemap-0.xml</loc></sitemap></sitemapindex>",
                "https://drivine.org/sitemap-0.xml" to urlset(
                    "https://drivine.org/",
                    "https://drivine.org/jvm/",
                    "https://drivine.org/jvm/license/",
                    "https://drivine.org/jvm/testing/",
                    "https://drivine.org/ts/quick-start/",
                    "https://drivine.org/jvm/testing/",
                ),
            )
        )

        assertEquals(
            listOf("https://drivine.org/jvm/", "https://drivine.org/jvm/testing/"),
            SitePages(fake).pages(drivine),
        )
    }

    @Test
    fun `site pages unescape the sitemap's XML entities`() {
        val site = SiteConfig(sitemap = "https://example.com/sitemap.xml", include = listOf("https://example.com/**"))
        val fake = FakeFetcher(
            mapOf("https://example.com/sitemap.xml" to urlset("https://example.com/a-&amp;-b/"))
        )

        assertEquals(listOf("https://example.com/a-&-b/"), SitePages(fake).pages(site))
    }

    @Test
    fun `a page read through its site's route keeps the page's URL and its headings`() {
        val fake = FakeFetcher(
            mapOf(
                "https://drivine.org/jvm/testing.md" to
                    "# Testing\n\nIntro.\n\n## Automated Test Configuration\n\nUse Testcontainers.\n"
            )
        )
        val routed = RoutingContentFetcher(
            FakeFetcher(emptyMap()),
            listOf("https://drivine.org/jvm/**" to MarkdownSiblingFetcher(fake)),
        )

        val document = TikaHierarchicalContentReader(routed).parseUrl("https://drivine.org/jvm/testing/")

        assertEquals("https://drivine.org/jvm/testing/", document.uri)
        val titles = document.descendants().map { it.title }.toList()
        assertTrue(titles.contains("Automated Test Configuration"), "sections: $titles")
    }
}
