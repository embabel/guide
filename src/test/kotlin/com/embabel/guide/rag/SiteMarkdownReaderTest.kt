package com.embabel.guide.rag

import com.embabel.agent.rag.ingestion.ChunkTransformationContext
import com.embabel.agent.rag.ingestion.ContentFetcher
import com.embabel.agent.rag.ingestion.FetchResult
import com.embabel.agent.rag.ingestion.HierarchicalContentReader
import com.embabel.agent.rag.ingestion.InMemoryContentChunker
import com.embabel.agent.rag.ingestion.ContentChunker
import com.embabel.agent.rag.model.Chunk
import com.embabel.agent.rag.model.LeafSection
import com.embabel.agent.rag.model.NavigableDocument
import com.embabel.agent.rag.model.Section
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.net.URI

class SiteMarkdownReaderTest {

    private val page = "https://hub.embabel.com/reference/rag"

    private fun leaves(document: NavigableDocument) = document.descendants().filterIsInstance<LeafSection>().toList()

    private fun leaf(document: NavigableDocument, title: String) =
        leaves(document).single { it.title == title && !it.id.endsWith("_preamble") }

    @Test
    fun `a heading's anchor leaves its title and becomes its section's fragment`() {
        val document = SiteMarkdown.parse(
            "# RAG {#rag}\n\nIntro.\n\n## ToolishRag lifecycle {#toolish-rag-lifecycle}\n\nIt starts.\n",
            page,
        )

        assertEquals("RAG", document.title)
        val lifecycle = leaf(document, "ToolishRag lifecycle")
        assertEquals("$page#toolish-rag-lifecycle", lifecycle.uri)
        assertEquals("It starts.", lifecycle.text)
    }

    @Test
    fun `a section without an anchor has the URI of the nearest section above it with one, or else the page's`() {
        val document = SiteMarkdown.parse(
            "# RAG\n\nIntro.\n\n## Usage {#usage}\n\n### Setup {#setup}\n\n#### Deeper\n\nDetail.\n\n## Plain\n\nText.\n",
            page,
        )

        assertEquals("$page#setup", leaf(document, "Deeper").uri)
        assertEquals(page, leaf(document, "Plain").uri)
        assertEquals(page, document.children.single().uri)
    }

    @Test
    fun `hash lines inside fenced code are content, not headings`() {
        val markdown = """
            |## Running {#running}
            |
            |```bash
            |# Clone and run examples
            |git clone https://github.com/embabel/embabel-agent-examples
            |```
            |
            |~~~yaml
            |# A comment
            |key: value
            |~~~
            |
            |````markdown
            |```
            |# still code
            |```
            |````
            |
            |Done.
        """.trimMargin()

        val document = SiteMarkdown.parse(markdown, page)

        assertEquals(listOf("Running"), leaves(document).map { it.title })
        val text = leaves(document).single().text
        assertTrue(text.contains("# Clone and run examples\ngit clone"), text)
        assertTrue(text.contains("# A comment\nkey: value"), text)
        assertTrue(text.contains("# still code"), text)
        assertTrue(text.endsWith("Done."), text)
    }

    @Test
    fun `braces in a title that aren't an anchor stay in the title`() {
        val document = SiteMarkdown.parse("## Map<String, {x}>\n\nText.\n\n##   Spaced   {#spaced}  \n\nMore.\n", page)

        assertEquals(page, leaf(document, "Map<String, {x}>").uri)
        assertEquals("$page#spaced", leaf(document, "Spaced").uri)
    }

    @Test
    fun `a section with subsections keeps its own text first, as embabel-agent's parser does`() {
        val document = SiteMarkdown.parse("# RAG {#rag}\n\nIntro.\n\n## Usage {#usage}\n\nUse it.\n", page)

        val top = document.children.single()
        assertEquals("RAG", top.title)
        val children = top.children.toList()
        assertEquals(listOf("RAG", "Usage"), children.map { it.title })
        assertEquals("Intro.", (children[0] as LeafSection).text)
        assertEquals("$page#rag", children[0].uri)
    }

    @Test
    fun `text before the first heading is a section titled from the page`() {
        val document = SiteMarkdown.parse("Lead in.\n\n# Testing {#testing}\n\nBody.\n", page)

        assertEquals(listOf("Testing", "Testing"), leaves(document).map { it.title })
        assertEquals("Lead in.", leaves(document).first().text)
    }

    @Test
    fun `site pages are read as site markdown, through the fetcher, and everything else by the delegate`() {
        val site = SiteConfig(sitemap = "https://hub.embabel.com/sitemap.xml", include = listOf("https://hub.embabel.com/**"))
        val fetched = mutableListOf<URI>()
        val fetcher = ContentFetcher { uri ->
            fetched += uri
            FetchResult("## Usage {#usage}\n\nUse it.\n".toByteArray())
        }
        val delegate = mock(HierarchicalContentReader::class.java)
        val other = mock(NavigableDocument::class.java)
        `when`(delegate.parseUrl("https://medium.com/@springrod/post")).thenReturn(other)
        val reader = SiteMarkdownReader(listOf(site), fetcher, delegate)

        val document = reader.parseUrl(page)
        assertEquals(page, document.uri)
        assertEquals("$page#usage", leaf(document, "Usage").uri)
        assertEquals(listOf(URI(page)), fetched)

        assertSame(other, reader.parseUrl("https://medium.com/@springrod/post"))
        verify(delegate).parseUrl("https://medium.com/@springrod/post")
    }

    @Test
    fun `a site page is never handed to the delegate`() {
        val site = SiteConfig(sitemap = "https://hub.embabel.com/sitemap.xml", include = listOf("https://hub.embabel.com/**"))
        val delegate = mock(HierarchicalContentReader::class.java)
        SiteMarkdownReader(listOf(site), { FetchResult("# A\n".toByteArray()) }, delegate).parseUrl(page)
        verifyNoInteractions(delegate)
    }

    @Test
    fun `each chunk's url is its section's, anchor and all`() {
        val document = SiteMarkdown.parse(
            "# RAG {#rag}\n\nIntro.\n\n## Usage {#usage}\n\n" + "Use it. ".repeat(700) + "\n\n## Tips {#tips}\n\n" +
                "Tip. ".repeat(700) + "\n",
            page,
        )
        val chunker = InMemoryContentChunker(ContentChunker.Config(maxChunkSize = 4000, overlapSize = 200))
        val urls = chunker.chunk(document).map { chunk ->
            SectionUrlChunkTransformer.transform(chunk, ChunkTransformationContext(document, document)).uri
        }

        assertTrue(urls.contains("$page#usage"), "$urls")
        assertTrue(urls.contains("$page#tips"), "$urls")
        assertTrue(urls.none { it == null }, "$urls")
    }

    @Test
    fun `a chunk with no section URL takes its document's`() {
        val chunk = Chunk.create(text = "Text.", parentId = "p")
        val document = SiteMarkdown.parse("# Page\n\nText.\n", "https://example.com/page")
        val context = ChunkTransformationContext(document.descendants().filterIsInstance<Section>().first(), document)

        assertEquals("https://example.com/page", SectionUrlChunkTransformer.transform(chunk, context).uri)
    }
}
