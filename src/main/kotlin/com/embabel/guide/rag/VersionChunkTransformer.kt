package com.embabel.guide.rag

import com.embabel.agent.rag.ingestion.AbstractChunkTransformer
import com.embabel.agent.rag.ingestion.ChunkTransformationContext
import com.embabel.agent.rag.model.Chunk
import com.embabel.guide.GuideProperties
import com.embabel.guide.VersionedContentConfig

/**
 * Stamps chunks with a `version` metadata property based on the source document URI.
 *
 * - The versioned docs (pages of [VersionedContentConfig.site]): the docs version.
 * - Everything else: "supplementary".
 *
 * This enables version-aware search filtering via [PropertyFilter].
 */
class VersionChunkTransformer(
    private val guideProperties: GuideProperties,
) : AbstractChunkTransformer() {

    override fun additionalMetadata(chunk: Chunk, context: ChunkTransformationContext): Map<String, Any> {
        val content = guideProperties.content
        val uri = context.document?.uri
        val version = content.activeVersion
        if (uri != null && version != null && content.versioned.site?.ingests(uri) == true) {
            return mapOf("version" to version)
        }
        return mapOf("version" to SUPPLEMENTARY)
    }

    companion object {
        const val SUPPLEMENTARY = "supplementary"
    }
}
