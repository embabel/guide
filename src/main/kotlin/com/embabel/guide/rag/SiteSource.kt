package com.embabel.guide.rag

import com.embabel.agent.rag.ingestion.ContentFetcher
import com.embabel.agent.rag.ingestion.FetchResult
import com.embabel.guide.VersionedContentConfig
import org.springframework.util.AntPathMatcher
import org.springframework.util.MimeType
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * A site whose pages are ingested one document to a page, each under the page's own URL, from the
 * markdown the site serves beside it (`/jvm/testing/` as `/jvm/testing.md`; see [MarkdownSiblingFetcher]).
 *
 * @param sitemap the site's sitemap: a `urlset` of pages, or a `sitemapindex` of further sitemaps
 * @param include Ant patterns of page URLs to ingest, such as `https://drivine.org/jvm/` then `**` for
 *                every page under `/jvm/`
 * @param exclude Ant patterns of page URLs to leave out, though included
 */
data class SiteConfig(
    val sitemap: String,
    val include: List<String>,
    val exclude: List<String> = emptyList(),
) {
    private val matcher get() = AntPathMatcher()

    /** Whether this site ingests the page at [url]. */
    fun ingests(url: String): Boolean =
        include.any { matcher.match(it, url) } && exclude.none { matcher.match(it, url) }
}

/**
 * The pages of a [SiteConfig], read from its sitemap: each `<loc>` the site [SiteConfig.ingests], in the
 * sitemap's order. A sitemap index is followed to the sitemaps it lists.
 */
class SitePages(private val fetcher: ContentFetcher) {

    fun pages(site: SiteConfig): List<String> = locs(site.sitemap).filter(site::ingests).distinct()

    /**
     * The pages of the docs' site, once its [VersionedContentConfig.versionUrl] says they're at the
     * docs version. Until it does (the site not yet rebuilt after the version changed) this throws, and
     * none of them is ingested: they would go into the store stamped with a version they aren't.
     */
    fun pages(docs: VersionedContentConfig): List<String> {
        val site = requireNotNull(docs.site) { "The versioned docs have no site" }
        docs.versionUrl?.let { url ->
            val published = fetcher.fetch(URI(url)).content.toString(StandardCharsets.UTF_8).trim()
            check(published == docs.version) {
                "$url says the docs at ${site.sitemap} are at $published, not at the docs version " +
                    "${docs.version}: they aren't ingested until it does"
            }
        }
        return pages(site)
    }

    private fun locs(sitemap: String, depth: Int = 0): List<String> {
        val xml = fetcher.fetch(URI(sitemap)).content.toString(StandardCharsets.UTF_8)
        val locs = LOC.findAll(xml).map { unescape(it.groupValues[1].trim()) }.toList()
        if (!xml.contains("<sitemapindex")) return locs
        require(depth < MAX_INDEX_DEPTH) { "Sitemap index nested more than $MAX_INDEX_DEPTH deep at $sitemap" }
        return locs.flatMap { locs(it, depth + 1) }
    }

    private fun unescape(text: String) = text
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&amp;", "&")

    private companion object {
        val LOC = Regex("<loc>([^<]+)</loc>")
        const val MAX_INDEX_DEPTH = 3
    }
}

/**
 * Fetches a page's markdown from beside it, where the site serves it: `https://drivine.org/jvm/testing/`
 * from `https://drivine.org/jvm/testing.md`, and a site's root page from `/index.md`. The document keeps
 * the page's URL, so links go to the page.
 */
class MarkdownSiblingFetcher(private val delegate: ContentFetcher) : ContentFetcher {

    override fun fetch(uri: URI): FetchResult {
        val markdown = markdownOf(uri)
        val result = delegate.fetch(markdown)
        // A site that serves its app for every path (Embabel Hub) answers a missing page with HTML.
        check(result.contentType?.isCompatibleWith(HTML) != true) { "$markdown is HTML, not markdown: no such page" }
        return FetchResult(result.content, MARKDOWN)
    }

    companion object {
        private val MARKDOWN = MimeType("text", "markdown", StandardCharsets.UTF_8)
        private val HTML = MimeType("text", "html")

        /**
         * The markdown beside the page at [page]: its path without the trailing slash, then `.md`; for
         * the site's root page, `/index.md`.
         */
        @JvmStatic
        fun markdownOf(page: URI): URI {
            val path = page.path.trimEnd('/').ifEmpty { "/index" }
            return URI(page.scheme, page.authority, "$path.md", null, null)
        }
    }
}
