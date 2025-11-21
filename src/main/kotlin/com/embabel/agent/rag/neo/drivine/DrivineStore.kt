package com.embabel.agent.rag.neo.drivine

import com.embabel.agent.rag.ingestion.RetrievableEnhancer
import com.embabel.agent.rag.model.*
import com.embabel.agent.rag.store.ChunkingContentElementRepository
import com.embabel.agent.rag.store.DocumentDeletionResult

class DrivineStore : ChunkingContentElementRepository {

    override fun deleteRootAndDescendants(uri: String): DocumentDeletionResult? {
        TODO("Not yet implemented")
    }

    override fun findContentRootByUri(uri: String): ContentRoot? {
        TODO("Not yet implemented")
    }

    override fun onNewRetrievables(retrievables: List<Retrievable>) {
        TODO("Not yet implemented")
    }

    override fun writeAndChunkDocument(root: NavigableDocument): List<String> {
        TODO("Not yet implemented")
    }

    override val enhancers: List<RetrievableEnhancer>
        get() = TODO("Not yet implemented")

    override fun count(): Int {
        TODO("Not yet implemented")
    }

    override fun findAllChunksById(chunkIds: List<String>): Iterable<Chunk> {
        TODO("Not yet implemented")
    }

    override fun findById(id: String): ContentElement? {
        TODO("Not yet implemented")
    }

    override fun findChunksForEntity(entityId: String): List<Chunk> {
        TODO("Not yet implemented")
    }

    override fun save(element: ContentElement): ContentElement {
        TODO("Not yet implemented")
    }

    override val name: String
        get() = TODO("Not yet implemented")
}