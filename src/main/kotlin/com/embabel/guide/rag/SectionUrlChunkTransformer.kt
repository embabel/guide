package com.embabel.guide.rag

import com.embabel.agent.rag.ingestion.AbstractChunkTransformer
import com.embabel.agent.rag.ingestion.ChunkTransformationContext
import com.embabel.agent.rag.model.Chunk
import com.embabel.agent.rag.model.ChunkStructure

/**
 * Gives each chunk a `url`: the URL of the section it came from (with the section's anchor, for site pages
 * read by [SiteMarkdownReader]), or failing that its document's.
 *
 * Search results show the model a chunk's `url` (`Chunk.uri` reads `metadata["url"]`), and the chunker sets
 * none, so without this the model sees no link at all and makes them up.
 */
object SectionUrlChunkTransformer : AbstractChunkTransformer() {

    override fun additionalMetadata(chunk: Chunk, context: ChunkTransformationContext): Map<String, Any> {
        val url = chunk.metadata[ChunkStructure.LEAF_SECTION_URL] as? String
            ?: chunk.metadata[ChunkStructure.CONTAINER_SECTION_URL] as? String
            ?: context.document?.uri
        return if (url.isNullOrBlank()) emptyMap() else mapOf(URL to url)
    }

    const val URL = "url"
}
