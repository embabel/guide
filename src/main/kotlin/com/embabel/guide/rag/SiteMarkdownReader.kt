package com.embabel.guide.rag

import com.embabel.agent.rag.ingestion.ContentFetcher
import com.embabel.agent.rag.ingestion.HierarchicalContentReader
import com.embabel.agent.rag.model.DefaultMaterializedContainerSection
import com.embabel.agent.rag.model.LeafSection
import com.embabel.agent.rag.model.MaterializedDocument
import com.embabel.agent.rag.model.NavigableDocument
import com.embabel.agent.rag.model.NavigableSection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Reads the pages of [sites] from the markdown beside each ([MarkdownSiblingFetcher]) with [SiteMarkdown], and
 * everything else with [delegate].
 *
 * A stopgap until embabel-agent's markdown parser reads headings only outside code and takes a heading's `{#id}`
 * as its section's anchor (embabel/embabel-agent#2221): then the sites can go back through [delegate].
 */
class SiteMarkdownReader(
    private val sites: List<SiteConfig>,
    private val fetcher: ContentFetcher,
    private val delegate: HierarchicalContentReader,
) : HierarchicalContentReader by delegate {

    override fun parseUrl(url: String): NavigableDocument =
        if (sites.any { it.ingests(url) }) {
            SiteMarkdown.parse(fetcher.fetch(URI(url)).content.toString(StandardCharsets.UTF_8), url)
        } else {
            delegate.parseUrl(url)
        }
}

/**
 * Markdown to a document of sections, as embabel-agent's markdown parser makes them, with two differences:
 * - a line inside fenced code is content, never a heading, though it starts with `#`;
 * - a heading's trailing `{#id}`, as the sites write the anchor the page gives it, leaves the title and becomes
 *   the fragment of the section's URI. A section without one has the URI of the nearest section above it that
 *   has one (a site anchors headings to level 3 at most), or else the page's.
 */
object SiteMarkdown {

    private val FENCE = Regex("""^ {0,3}(`{3,}|~{3,})""")
    private val HEADING = Regex("""^(#{1,6})(?:\s+(.*?))?\s*$""")
    private val ANCHOR = Regex("""^(.*?)\s*\{#([^\s{}]+)\}$""")

    private class Heading(val level: Int, val title: String, val anchor: String?)

    fun parse(markdown: String, uri: String): MaterializedDocument {
        val rootId = UUID.randomUUID().toString()
        val leaves = mutableListOf<LeafSection>()
        val parents = mutableMapOf<Int, LeafSection>() // level -> the latest section at that level
        val metadata = mapOf("Content-Type-Hint" to "text/markdown")

        var heading: Heading? = null
        var sectionId = ""
        var parent: LeafSection? = null
        val text = StringBuilder()
        var fence: String? = null

        fun close() {
            val current = heading ?: return
            val sectionUri = current.anchor?.let { withFragment(uri, it) } ?: parent?.uri ?: uri
            val leaf = LeafSection(
                id = sectionId,
                uri = sectionUri,
                title = current.title,
                text = text.toString().trim(),
                parentId = parent?.id ?: rootId,
                metadata = metadata + mapOf(
                    "root_document_id" to rootId,
                    "container_section_id" to (parent?.id ?: rootId),
                    "leaf_section_id" to sectionId,
                ),
            )
            leaves += leaf
            parents[current.level] = leaf
            parents.keys.filter { it > current.level }.forEach(parents::remove)
        }

        for (line in markdown.lines()) {
            val opening = FENCE.find(line)?.groupValues?.get(1)
            val match = if (fence == null && opening == null) HEADING.matchEntire(line) else null
            when {
                fence != null -> {
                    if (opening != null && opening[0] == fence!![0] && opening.length >= fence!!.length) fence = null
                    text.appendLine(line)
                }
                opening != null -> {
                    fence = opening
                    text.appendLine(line)
                }
                match != null -> {
                    close()
                    val level = match.groupValues[1].length
                    val raw = match.groupValues[2]
                    val anchored = ANCHOR.matchEntire(raw)
                    heading = Heading(
                        level,
                        anchored?.groupValues?.get(1) ?: raw,
                        anchored?.groupValues?.get(2),
                    )
                    sectionId = UUID.randomUUID().toString()
                    parent = (level - 1 downTo 1).firstNotNullOfOrNull { parents[it] }
                    text.clear()
                }
                heading != null && (line.isNotBlank() || text.isNotEmpty()) -> text.appendLine(line)
                heading == null && line.isNotBlank() -> {
                    // Text before the first heading: a section of its own, titled from the page.
                    heading = Heading(0, "", null)
                    sectionId = UUID.randomUUID().toString()
                    text.appendLine(line)
                }
            }
        }
        close()

        val title = leaves.firstOrNull { it.title.isNotBlank() }?.title ?: "Document"
        return MaterializedDocument(
            id = rootId,
            uri = uri,
            title = title,
            children = hierarchy(leaves.map { if (it.title.isBlank()) it.copy(title = title) else it }, rootId),
            metadata = metadata,
        )
    }

    /** [uri] with [fragment] in place of any fragment it has. */
    fun withFragment(uri: String, fragment: String): String = uri.substringBefore('#') + "#" + fragment

    /**
     * Sections with children become containers, as embabel-agent's parser builds them; a container's own text
     * goes first, as a leaf of its own.
     */
    private fun hierarchy(leaves: List<LeafSection>, rootId: String): List<NavigableSection> {
        val byParent = leaves.groupBy { it.parentId }

        fun build(leaf: LeafSection): NavigableSection {
            val children = byParent[leaf.id] ?: return leaf
            val preamble = if (leaf.text.isBlank()) emptyList() else listOf(
                leaf.copy(
                    id = "${leaf.id}_preamble",
                    parentId = leaf.id,
                    metadata = leaf.metadata + ("leaf_section_id" to "${leaf.id}_preamble"),
                )
            )
            return DefaultMaterializedContainerSection(
                id = leaf.id,
                uri = leaf.uri,
                title = leaf.title,
                children = preamble + children.map(::build),
                parentId = leaf.parentId,
                metadata = leaf.metadata,
            )
        }

        return byParent[rootId].orEmpty().map(::build)
    }
}
