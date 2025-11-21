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
import org.drivine.manager.PersistenceManager
import org.drivine.query.QuerySpecification
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import kotlin.collections.get

@Service
class DrivineStore(
    @param:Qualifier("neo") val persistenceManager: PersistenceManager,
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

            val deletedCount = result.items().firstOrNull()?.get("deletedCount") as? Int ?: 0

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

        try {
            val statement = cypherContentElementQuery(
                " WHERE c.uri = \$uri AND ('Document' IN labels(c) OR 'ContentRoot' IN labels(c)) ")
            val spec = QuerySpecification
                .withStatement(statement)
                .bind(mapOf("uri" to uri))
                .transform(Map::class.java)
                .map({rowToContentElement(it)})

            val result = persistenceManager.maybeGetOne(spec)
            logger.debug("Root document with URI {} found: {}", uri, result != null)
            return result as? ContentRoot
        } catch (e: Exception) {
            logger.error("Error finding root with URI: {}", uri, e)
            return null
        }
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
        val nodesUpdated = result.items().firstOrNull()?.get("nodesUpdated") as? Int ?: 0
        if (nodesUpdated == 0) {
            logger.warn(
                "Expected to set embedding properties, but set 0. chunkId={}, cypher={}",
                retrievable.id,
                cypher,
            )
        }
    }

    override fun count(): Int {
        return cypherSearch.queryForInt("MATCH (c:ContentElement) RETURN count(c) AS count")
    }

    override fun findAllChunksById(chunkIds: List<String>): Iterable<Chunk> {
        val statement = cypherContentElementQuery(" WHERE c:Chunk AND c.id IN \$ids ")
        val spec = QuerySpecification
            .withStatement(statement)
            .bind(mapOf("ids" to chunkIds))
            .transform(Map::class.java)
            .map({rowToContentElement(it)})
            .filter { it is Chunk }
            .map { it as Chunk }

        return persistenceManager.query(spec)
    }

    override fun findById(id: String): ContentElement? {
        val statement = cypherContentElementQuery(" WHERE c.id = \$id ")
        val spec = QuerySpecification
            .withStatement(statement)
            .bind(mapOf("id" to id))
            .transform(Map::class.java)
            .map({rowToContentElement(it)})
        return persistenceManager.maybeGetOne(spec)
    }

    override fun findChunksForEntity(entityId: String): List<Chunk> {

        val statement = """
            MATCH (e:Entity {id: ${'$'}entityId})<-[:HAS_ENTITY]-(chunk:Chunk)
            RETURN properties(chunk)
            """.trimIndent()
        val spec = QuerySpecification
            .withStatement(statement)
            .bind(mapOf("entityId" to entityId))
            .transform(Map::class.java)
            .map({
                Chunk(
                    id = it["id"] as String,
                    text = it["text"] as String,
                    parentId = it["parentId"] as String,
                    metadata = emptyMap(), //TODO Can it ever be populated?
                )
            })
        return persistenceManager.query(spec)
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

    private fun cypherContentElementQuery(whereClause: String): String =
        """
            MATCH (c:ContentElement)
            $whereClause
            RETURN
              {
                id: c.id,
                uri: c.uri,
                text: c.text,
                parentId: c.parentId,
                ingestionDate: c.ingestionTimestamp,
                metadata_source: c.metadata.source,
                labels: labels(c)
              } AS result
            """.trimIndent()

    private fun rowToContentElement(row: Map<*, *>): ContentElement {
        val metadata = mutableMapOf<String, Any>()
        metadata["source"] = row["metadata_source"] ?: "unknown"
        val labels = row["labels"] as? Array<String> ?: error("Must have labels")
        if (labels.contains("Chunk"))
            return Chunk(
                id = row["id"] as String,
                text = row["text"] as String,
                parentId = row["parentId"] as String,
                metadata = metadata,
            )
        if (labels.contains("Document")) {
            val ingestionDate = when (val rawDate = row["ingestionDate"]) {
                is java.time.Instant -> rawDate
                is java.time.ZonedDateTime -> rawDate.toInstant()
                is Long -> java.time.Instant.ofEpochMilli(rawDate)
                is String -> java.time.Instant.parse(rawDate)
                null -> java.time.Instant.now()
                else -> java.time.Instant.now()
            }
            return MaterializedDocument(
                id = row["id"] as String,
                title = row["id"] as String,
                children = emptyList(),
                metadata = metadata,
                uri = row["uri"] as String,
                ingestionTimestamp = ingestionDate,
            )
        }
        throw RuntimeException("Don't know how to map: $labels")
    }

}
