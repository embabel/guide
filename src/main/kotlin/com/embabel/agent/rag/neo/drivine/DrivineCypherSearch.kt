package com.embabel.agent.rag.neo.drivine

import com.embabel.agent.rag.model.*
import com.embabel.agent.rag.neo.drivine.mappers.ChunkSimilarityMapper
import com.embabel.agent.rag.neo.drivine.mappers.ContentElementMapper
import com.embabel.agent.rag.neo.drivine.mappers.EntityDataMapper
import com.embabel.agent.rag.neo.drivine.mappers.EntityDataSimilarityMapper
import com.embabel.agent.rag.service.Cluster
import com.embabel.agent.rag.service.ClusterFinder
import com.embabel.agent.rag.service.ClusterRetrievalRequest
import com.embabel.common.core.types.SimilarityResult
import com.embabel.common.core.types.SimpleSimilaritySearchResult
import org.drivine.manager.PersistenceManager
import org.drivine.query.QuerySpecification
import org.drivine.utils.ObjectUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DrivineCypherSearch(
    private val persistenceManager: PersistenceManager,
    private val queryResolver: LogicalQueryResolver,
    private val contentElementMapper: ContentElementMapper,
    private val entityDataMapper: EntityDataMapper,
    private val entityDataSimilarityMapper: EntityDataSimilarityMapper,
    private val chunkSimilarityMapper: ChunkSimilarityMapper,
) : CypherSearch, ClusterFinder {

    private val logger = LoggerFactory.getLogger(DrivineCypherSearch::class.java)

    override fun createEntity(
        entity: NamedEntityData,
        basis: Retrievable,
    ): String {
        val params = mapOf(
            "id" to entity.id,
            "name" to entity.name,
            "description" to entity.description,
            "basisId" to basis.id,
            "properties" to entity.properties,
            "chunkNodeName" to "Chunk",
            "entityLabels" to entity.labels(),
        )
        val result = query(
            purpose = "Create entity",
            query = "create_entity",
            params = params,
            logger = logger,
        )
        val singleRow = result.singleOrNull() ?: error("No result returned from create_entity")
        val id = singleRow["id"] as? String ?: error("No id returned from create_entity")
        logger.info("Created entity {} with id: {}", entity.labels(), id)
        return id
    }

    override fun <T> loadEntity(
        type: Class<T>,
        id: String,
    ): T? {
        // Drivine doesn't support OGM-style entity loading
        // This would need to be implemented differently if needed
        throw UnsupportedOperationException("loadEntity is not supported in DrivineCypherSearch")
    }

    override fun queryForEntities(
        purpose: String,
        query: String,
        params: Map<String, *>,
        logger: Logger?,
    ): List<EntityData> {
        val loggerToUse = logger ?: this.logger
        val cypher = if (query.contains(" ")) query else queryResolver.resolve(query)!!
        loggerToUse.info("[{}] query\n\tparams: {}\n{}", purpose, params, cypher)

        val parameters = ObjectUtils.primitiveProps(params)

        return persistenceManager.query(
            QuerySpecification
                .withStatement(cypher)
                .bind(parameters)
                .mapWith(entityDataMapper)
        )
    }

    override fun entityDataSimilaritySearch(
        purpose: String,
        query: String,
        params: Map<String, *>,
        logger: Logger?,
    ): List<SimilarityResult<EntityData>> {
        val loggerToUse = logger ?: this.logger
        val cypher = if (query.contains(" ")) query else queryResolver.resolve(query)!!
        loggerToUse.info("[{}] query\n\tparams: {}\n{}", purpose, params, cypher)

        val parameters = ObjectUtils.primitiveProps(params)

        return persistenceManager.query(
            QuerySpecification
                .withStatement(cypher)
                .bind(parameters)
                .mapWith(entityDataSimilarityMapper)
        )
    }

    override fun chunkSimilaritySearch(
        purpose: String,
        query: String,
        params: Map<String, *>,
        logger: Logger?,
    ): List<SimilarityResult<Chunk>> {
        val loggerToUse = logger ?: this.logger
        val cypher = if (query.contains(" ")) query else queryResolver.resolve(query)!!
        loggerToUse.info("[{}] query\n\tparams: {}\n{}", purpose, params, cypher)

        val parameters = ObjectUtils.primitiveProps(params)

        return persistenceManager.query(
            QuerySpecification
                .withStatement(cypher)
                .bind(parameters)
                .mapWith(chunkSimilarityMapper)
        )
    }

    override fun chunkFullTextSearch(
        purpose: String,
        query: String,
        params: Map<String, *>,
        logger: Logger?,
    ): List<SimilarityResult<Chunk>> {
        val loggerToUse = logger ?: this.logger
        val cypher = if (query.contains(" ")) query else queryResolver.resolve(query)!!
        loggerToUse.info("[{}] query\n\tparams: {}\n{}", purpose, params, cypher)

        val parameters = ObjectUtils.primitiveProps(params)

        return persistenceManager.query(
            QuerySpecification
                .withStatement(cypher)
                .bind(parameters)
                .mapWith(chunkSimilarityMapper)
        )
    }

    override fun entityFullTextSearch(
        purpose: String,
        query: String,
        params: Map<String, *>,
        logger: Logger?,
    ): List<SimilarityResult<EntityData>> {
        val loggerToUse = logger ?: this.logger
        val cypher = if (query.contains(" ")) query else queryResolver.resolve(query)!!
        loggerToUse.info("[{}] query\n\tparams: {}\n{}", purpose, params, cypher)

        val parameters = ObjectUtils.primitiveProps(params)

        return persistenceManager.query(
            QuerySpecification
                .withStatement(cypher)
                .bind(parameters)
                .mapWith(entityDataSimilarityMapper)
        )
    }

    /**
     * Execute a query and return results as QueryResult
     */
    override fun query(
        purpose: String,
        query: String,
        params: Map<String, *>,
        logger: Logger?,
    ): QueryResult {
        val loggerToUse = logger ?: this.logger
        val cypher = if (query.contains(" ")) query else queryResolver.resolve(query)!!
        loggerToUse.info("[{}] query\n\tparams: {}\n{}", purpose, params, cypher)

        val parameters = ObjectUtils.primitiveProps(params)

        @Suppress("UNCHECKED_CAST")
        val rows = persistenceManager.query(
            QuerySpecification
                .withStatement(cypher)
                .bind(parameters)
                .transform(Map::class.java)
        ) as List<Map<String, Any>>

        return QueryResult(rows)
    }

    override fun queryForInt(
        query: String,
        params: Map<String, *>,
    ): Int {
        val cypher = if (query.contains(" ")) query else queryResolver.resolve(query)!!
        val results = persistenceManager.getOne(
            QuerySpecification
                .withStatement(cypher)
                .bind(params)
                .transform(Int::class.java)
        )
        return results
    }

    @Transactional(readOnly = true)
    override fun <E> findClusters(opts: ClusterRetrievalRequest<E>): List<Cluster<E>> {
        val labels = opts.entitySearch?.labels?.toList() ?: error("Must specify labels in entity search for clustering")
        val params = mapOf(
            "labels" to labels,
            "vectorIndex" to opts.vectorIndex,
            "similarityThreshold" to opts.similarityThreshold,
            "topK" to opts.topK,
        )
        val result = query(
            purpose = "cluster",
            query = "vector_cluster",
            params = params,
        )
        return result.map { row ->
            val anchorMap = row["anchor"] as? Map<String, *> ?: error("Expected anchor in row")
            val anchor = contentElementMapper.map(anchorMap) as E

            val similar = row["similar"] as? List<*> ?: emptyList<E>()
            val similarityResults = similar.mapNotNull { similarItem ->
                try {
                    val similarMap = similarItem as? Map<*, *> ?: return@mapNotNull null
                    val matchMap = similarMap["match"] as? Map<String, *> ?: return@mapNotNull null
                    val score = similarMap["score"] as? Double ?: return@mapNotNull null

                    val matchElement = contentElementMapper.map(matchMap)
                    val match = matchElement as E
                    logger.debug("Found match: {} with score {}", matchElement.id, "%.2f".format(score))
                    SimpleSimilaritySearchResult(match, score) as SimilarityResult<E>
                } catch (e: Exception) {
                    logger.warn("Could not map similar item: {}", similarItem, e)
                    null
                }
            }
            Cluster(anchor, similarityResults)
        }
    }
}
