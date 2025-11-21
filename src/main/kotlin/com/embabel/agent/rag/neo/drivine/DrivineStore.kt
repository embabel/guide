package com.embabel.agent.rag.neo.drivine

import com.embabel.agent.api.common.Embedding
import com.embabel.agent.rag.ingestion.RetrievableEnhancer
import com.embabel.agent.rag.model.*
import com.embabel.agent.rag.service.RagRequest
import com.embabel.agent.rag.service.support.FunctionRagFacet
import com.embabel.agent.rag.service.support.RagFacet
import com.embabel.agent.rag.service.support.RagFacetProvider
import com.embabel.agent.rag.service.support.RagFacetResults
import com.embabel.agent.rag.store.AbstractChunkingContentElementRepository
import com.embabel.agent.rag.store.ChunkingContentElementRepository
import com.embabel.agent.rag.store.DocumentDeletionResult
import com.embabel.common.ai.model.DefaultModelSelectionCriteria
import com.embabel.common.ai.model.ModelProvider
import com.embabel.common.core.types.SimilarityResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DrivineStore(
    override val enhancers: List<RetrievableEnhancer> = emptyList(),
    val properties: NeoRagServiceProperties,
    private val cypherSearch: CypherSearch,
    modelProvider: ModelProvider,
) : AbstractChunkingContentElementRepository(properties), ChunkingContentElementRepository, RagFacetProvider {

    private val logger = LoggerFactory.getLogger(DrivineStore::class.java)

    private val embeddingService = modelProvider.getEmbeddingService(DefaultModelSelectionCriteria)

    override val name get() = properties.name

    override fun commit() {
        // TODO may need to do this?
    }

    override fun createRelationships(root: NavigableDocument) {
        println("TODO: createRelationships not yet implemented")
    }

    override fun deleteRootAndDescendants(uri: String): DocumentDeletionResult? {
        logger.info("Deleting document with URI: {}", uri)

        try {
            val result = cypherSearch.query(
                "Delete document and descendants",
                query = "delete_document_and_descendants",
                params = mapOf("uri" to uri)
            )

            val deletedCount = TODO()//result.queryStatistics().nodesDeleted

            if (deletedCount == 0) {
                logger.warn("No document found with URI: {}", uri)
                return null
            }

            logger.info("Deleted {} elements for document with URI: {}", deletedCount, uri)
            return DocumentDeletionResult(
                rootUri = uri,
                deletedCount = deletedCount
            )
        } catch (e: Exception) {
            logger.error("Error deleting document with URI: {}", uri, e)
            throw e
        }
    }

    override fun findContentRootByUri(uri: String): ContentRoot? {
        logger.debug("Finding root document with URI: {}", uri)

//        try {
//            val session = cypherSearch.currentSession()
//            val rows = session.query(
//                cypherContentElementQuery(" WHERE c.uri = \$uri AND ('Document' IN labels(c) OR 'ContentRoot' IN labels(c)) "),
//                mapOf("uri" to uri),
//                true
//            )
//
//            val element = rows.mapNotNull(::rowToContentElement).firstOrNull()
//            logger.debug("Root document with URI {} found: {}", uri, element != null)
//            return element as? ContentRoot
//        } catch (e: Exception) {
//            logger.error("Error finding root with URI: {}", uri, e)
//            return null
//        }
        TODO()
    }

    override fun onNewRetrievables(retrievables: List<Retrievable>) {
        retrievables.forEach { embedRetrievable(it) }
    }

    fun embeddingFor(text: String): Embedding =
        embeddingService.model.embed(text)

    private fun embedRetrievable(
        retrievable: Retrievable,
    ) {
        val embedding = embeddingFor(retrievable.embeddableValue())
        val cypher = """
                MERGE (n:${retrievable.labels().joinToString(":")} {id: ${'$'}id})
                SET n.embedding = ${'$'}embedding,
                 n.embeddingModel = ${'$'}embeddingModel,
                 n.embeddedAt = timestamp()
                RETURN COUNT(n) as nodesUpdated
               """.trimIndent()
        val params = mapOf(
            "id" to retrievable.id,
            "embedding" to embedding,
            "embeddingModel" to embeddingService.name,
        )
        val result = cypherSearch.query(
            purpose = "embedding",
            query = cypher,
            params = params,
        )
//        val propertiesSet = result.queryStatistics().propertiesSet
//        if (propertiesSet == 0) {
//            logger.warn(
//                "Expected to set embedding properties, but set 0. chunkId={}, cypher={}",
//                retrievable.id,
//                cypher,
//            )
//        }
    }

    override fun count(): Int {
        return cypherSearch.queryForInt("MATCH (c:ContentElement) RETURN count(c) AS count")
    }

    override fun findAllChunksById(chunkIds: List<String>): Iterable<Chunk> {
//        val session = c.currentSession()
//        val rows = session.query(
//            cypherContentElementQuery(" WHERE c:Chunk AND c.id IN \$ids "),
//            mapOf("ids" to chunkIds),
//            true,
//        )
//        return rows.map(::rowToContentElement).filterIsInstance<Chunk>()
        TODO()
    }

    override fun findById(id: String): ContentElement? {
//        val session = cypherSearch.currentSession()
//        val rows = session.query(
//            cypherContentElementQuery(" WHERE c.id = \$id "),
//            mapOf("id" to id),
//            true,
//        )
//        return rows.mapNotNull(::rowToContentElement).firstOrNull()
        TODO("Not yet implemented")
    }

    override fun findChunksForEntity(entityId: String): List<Chunk> {
//        return cypherSearch.currentSession().query(
//            MappedChunk::class.java,
//            """
//            MATCH (e:Entity {id: ${'$'}entityId})<-[:HAS_ENTITY]-(chunk:Chunk)
//            RETURN chunk
//            """.trimIndent(),
//            mapOf("entityId" to entityId),
//        ).toList()
        TODO()
    }

    override fun save(element: ContentElement): ContentElement {
        cypherSearch.query(
            "Save element",
            query = "save_content_element",
            params = mapOf(
                "id" to element.id,
                "labels" to element.labels(),
                "properties" to element.propertiesToPersist(),
            )
        )
        return element
    }

    fun search(ragRequest: RagRequest): RagFacetResults<Retrievable> {
//        val embedding = embeddingService.model.embed(ragRequest.query)
//        val allResults = mutableListOf<SimilarityResult<out Retrievable>>()
//        if (ragRequest.contentElementSearch.types.contains(Chunk::class.java)) {
//            allResults += safelyExecuteInTransaction { chunkSearch(ragRequest, embedding) }
//        } else {
//            logger.info("No chunk search specified, skipping chunk search")
//        }
//
//        if (ragRequest.entitySearch != null) {
//            allResults += safelyExecuteInTransaction { entitySearch(ragRequest, embedding) }
//        } else {
//            logger.info("No entity search specified, skipping entity search")
//        }
//
//        // TODO should reward multiple matches
//        val mergedResults: List<SimilarityResult<out Retrievable>> = allResults
//            .distinctBy { it.match.id }
//            .sortedByDescending { it.score }
//            .take(ragRequest.topK)
        val mergedResults: List<SimilarityResult<out Retrievable>> = TODO()
        return RagFacetResults(
            facetName = this.name,
            results = mergedResults,
        )
    }

    override fun facets(): List<RagFacet<out Retrievable>> {
        return listOf(
            FunctionRagFacet(
                name = "DrivineRagService",
                searchFunction = ::search,
            )
        )
    }
}
