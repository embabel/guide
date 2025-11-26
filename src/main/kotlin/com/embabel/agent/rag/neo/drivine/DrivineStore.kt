package com.embabel.agent.rag.neo.drivine

import com.embabel.agent.api.common.Embedding
import com.embabel.agent.rag.ingestion.RetrievableEnhancer
import com.embabel.agent.rag.model.*
import com.embabel.agent.rag.neo.drivine.mappers.ContentElementMapper
import com.embabel.agent.rag.service.EntitySearch
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
import com.embabel.common.core.types.SimilarityCutoff
import com.embabel.common.core.types.SimilarityResult
import org.drivine.manager.PersistenceManager
import org.drivine.query.QuerySpecification
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@Service
class DrivineStore(
    @param:Qualifier("neo") val persistenceManager: PersistenceManager,
    override val enhancers: List<RetrievableEnhancer> = emptyList(),
    val properties: NeoRagServiceProperties,
    private val cypherSearch: CypherSearch,
    private val contentElementMapper: ContentElementMapper,
    modelProvider: ModelProvider,
    platformTransactionManager: PlatformTransactionManager,
) : AbstractChunkingContentElementRepository(properties), ChunkingContentElementRepository, RagFacetProvider {

    private val logger = LoggerFactory.getLogger(DrivineStore::class.java)

    private val embeddingService = modelProvider.getEmbeddingService(DefaultModelSelectionCriteria)

    override val name get() = properties.name

    override fun provision() {
        logger.info("Provisioning with properties {}", properties)
        // TODO do we want this on ContentElement?
        createVectorIndex(properties.contentElementIndex, "Chunk")
        createVectorIndex(properties.entityIndex, properties.entityNodeName)
        createFullTextIndex(properties.contentElementFullTextIndex, "Chunk", listOf("text"))
        createFullTextIndex(properties.entityFullTextIndex, properties.entityNodeName, listOf("name", "description"))
        logger.info("Provisioning complete")
    }

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

            val deletedCount = result.numberOrZero<Int>("deletedCount")

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

        val statement = cypherContentElementQuery(
            " WHERE c.uri = \$uri AND ('Document' IN labels(c) OR 'ContentRoot' IN labels(c)) "
        )
        val parameters = mapOf("uri" to uri)
        try {
            val spec = QuerySpecification
                .withStatement(statement)
                .bind(parameters)
                .mapWith(contentElementMapper)

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
                RETURN {nodesUpdated: COUNT(n) }
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
        val nodesUpdated = result.numberOrZero<Int>("nodesUpdated")
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
            .mapWith(contentElementMapper)
            .filterIsInstance<Chunk>()
        return persistenceManager.query(spec)
    }

    override fun findById(id: String): ContentElement? {
        val statement = cypherContentElementQuery(" WHERE c.id = \$id ")
        val spec = QuerySpecification
            .withStatement(statement)
            .bind(mapOf("id" to id))
            .mapWith(contentElementMapper)
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
        val embedding = embeddingService.model.embed(ragRequest.query)
        val allResults = mutableListOf<SimilarityResult<out Retrievable>>()
        if (ragRequest.contentElementSearch.types.contains(Chunk::class.java)) {
            allResults += safelyExecuteInTransaction { chunkSearch(ragRequest, embedding) }
        } else {
            logger.info("No chunk search specified, skipping chunk search")
        }

        if (ragRequest.entitySearch != null) {
            allResults += safelyExecuteInTransaction { entitySearch(ragRequest, embedding) }
        } else {
            logger.info("No entity search specified, skipping entity search")
        }

        // TODO should reward multiple matches
        val mergedResults: List<SimilarityResult<out Retrievable>> = allResults
            .distinctBy { it.match.id }
            .sortedByDescending { it.score }
            .take(ragRequest.topK)
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

    private val readonlyTransactionTemplate = TransactionTemplate(platformTransactionManager).apply {
        isReadOnly = true
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRED
    }

    private fun safelyExecuteInTransaction(block: () -> List<SimilarityResult<out Retrievable>>): List<SimilarityResult<out Retrievable>> {
        return try {
            readonlyTransactionTemplate.execute { block() } as List<SimilarityResult<out Retrievable>>
        } catch (e: Exception) {
            logger.error("Error during RAG search transaction", e)
            emptyList()
        }
    }

    private fun chunkSearch(
        ragRequest: RagRequest,
        embedding: Embedding,
    ): List<SimilarityResult<out Chunk>> {
        val chunkSimilarityResults = cypherSearch.chunkSimilaritySearch(
            "Chunk similarity search",
            query = "chunk_vector_search",
            params = commonParameters(ragRequest) + mapOf(
                "vectorIndex" to properties.contentElementIndex,
                "queryVector" to embedding,
            ),
            logger = logger,
        )
        logger.info("{} chunk similarity results for query '{}'", chunkSimilarityResults.size, ragRequest.query)

        val chunkFullTextResults = cypherSearch.chunkFullTextSearch(
            purpose = "Chunk full text search",
            query = "chunk_fulltext_search",
            params = commonParameters(ragRequest) + mapOf(
                "fulltextIndex" to properties.contentElementFullTextIndex,
                "searchText" to "\"${ragRequest.query}\"",
            ),
            logger = logger,
        )
        logger.info("{} chunk full-text results for query '{}'", chunkFullTextResults.size, ragRequest.query)
        return chunkSimilarityResults + chunkFullTextResults
    }

    private fun entitySearch(
        ragRequest: RagRequest,
        embedding: FloatArray,
    ): List<SimilarityResult<out Retrievable>> {
        val allEntityResults = mutableListOf<SimilarityResult<out Retrievable>>()
        val labels = ragRequest.entitySearch?.labels ?: error("No entity search specified")
        val entityResults = entityVectorSearch(
            ragRequest,
            embedding,
            labels,
        )
        allEntityResults += entityResults
        logger.info("{} entity vector results for query '{}'", entityResults.size, ragRequest.query)
        val entityFullTextResults = cypherSearch.entityFullTextSearch(
            purpose = "Entity full text search",
            query = "entity_fulltext_search",
            params = commonParameters(ragRequest) + mapOf(
                "fulltextIndex" to properties.entityFullTextIndex,
                "searchText" to ragRequest.query,
                "labels" to labels,
            ),
            logger = logger,
        )
        logger.info("{} entity full-text results for query '{}'", entityFullTextResults.size, ragRequest.query)
        allEntityResults += entityFullTextResults

        if (ragRequest.entitySearch?.generateQueries == true) {
            val cypherResults =
                generateAndExecuteCypher(ragRequest, ragRequest.entitySearch!!).also { cypherResults ->
                    logger.info("{} Cypher results for query '{}'", cypherResults.size, ragRequest.query)
                }
            allEntityResults += cypherResults
        } else {
            logger.info("No query generation specified, skipping Cypher generation and execution")
        }
        logger.info("{} total entity results for query '{}'", entityFullTextResults.size, ragRequest.query)
        return allEntityResults
    }

    fun entityVectorSearch(
        request: SimilarityCutoff,
        embedding: FloatArray,
        labels: Set<String>,
    ): List<SimilarityResult<out EntityData>> {
        return cypherSearch.entityDataSimilaritySearch(
            purpose = "Mapped entity search",
            query = "entity_vector_search",
            params = commonParameters(request) + mapOf(
                "index" to properties.entityIndex,
                "queryVector" to embedding,
                "labels" to labels,
            ),
            logger,
        )
    }

    private fun generateAndExecuteCypher(
        request: RagRequest,
        entitySearch: EntitySearch,
    ): List<SimilarityResult<out Retrievable>> {
        TODO("Not yet implemented")
//        val schema = schemaResolver.getSchema(entitySearch)
//        if (schema == null) {
//            logger.info("No schema found for entity search {}, skipping Cypher execution", entitySearch)
//            return emptyList()
//        }
//
//        val cypherRagQueryGenerator = SchemaDrivenCypherRagQueryGenerator(
//            modelProvider,
//            schema,
//        )
//        val cypher = cypherRagQueryGenerator.generateQuery(request = request)
//        logger.info("Generated Cypher query: $cypher")
//
//        val cypherResults = readonlyTransactionTemplate.execute {
//            executeGeneratedCypher(cypher)
//        } ?: Result.failure(
//            IllegalStateException("Transaction failed or returned null while executing Cypher query: $cypher")
//        )
//        if (cypherResults.isSuccess) {
//            val results = cypherResults.getOrThrow()
//            if (results.isNotEmpty()) {
//                logger.info("Cypher query executed successfully, results: {}", results)
//                return results.map {
//                    // Most similar as we found them by a query
//                    SimpleSimilaritySearchResult(
//                        it,
//                        score = 1.0,
//                    )
//                }
//            }
//        }
//        return emptyList()
    }

//    /**
//     * Execute generate Cypher query, being sure to handle exceptions gracefully.
//     */
//    private fun executeGeneratedCypher(
//        query: CypherQuery,
//    ): Result<List<EntityData>> {
//        TODO("Not yet implemented")
//        try {
//            return Result.success(
//                ogmCypherSearch.queryForEntities(
//                    purpose = "cypherGeneratedQuery",
//                    query = query.query
//                )
//            )
//        } catch (e: Exception) {
//            logger.error("Error executing generated query: $query", e)
//            return Result.failure(e)
//        }
//    }

    private fun createVectorIndex(
        name: String,
        on: String,
    ) {
        val statement = """
            CREATE VECTOR INDEX `$name` IF NOT EXISTS
            FOR (n:$on) ON (n.embedding)
            OPTIONS {indexConfig: {
            `vector.dimensions`: ${embeddingService.model.dimensions()},
            `vector.similarity_function`: 'cosine'
            }}"""

        persistenceManager.execute(QuerySpecification.withStatement(statement))

    }

    private fun createFullTextIndex(
        name: String,
        on: String,
        properties: List<String>,
    ) {
        val propertiesString = properties.joinToString(", ") { "n.$it" }
        val statement = """|
                |CREATE FULLTEXT INDEX `$name` IF NOT EXISTS
                |FOR (n:$on) ON EACH [$propertiesString]
                |OPTIONS {
                |indexConfig: {
                |
                |   }
                |}
                """.trimMargin()
        persistenceManager.execute(QuerySpecification.withStatement(statement))
        logger.info("Created full-text index {} for {} on properties {}", name, on, properties)
    }


    private fun commonParameters(request: SimilarityCutoff) = mapOf(
        "topK" to request.topK,
        "similarityThreshold" to request.similarityThreshold,
    )


}
