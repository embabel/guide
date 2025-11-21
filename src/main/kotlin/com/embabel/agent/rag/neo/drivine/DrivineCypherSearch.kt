package com.embabel.agent.rag.neo.drivine

import com.embabel.agent.rag.model.Chunk
import com.embabel.agent.rag.model.EntityData
import com.embabel.agent.rag.model.NamedEntityData
import com.embabel.agent.rag.model.Retrievable
import com.embabel.common.core.types.SimilarityResult
import org.slf4j.Logger
import org.springframework.stereotype.Service

@Service
class DrivineCypherSearch : CypherSearch {
    override fun createEntity(
        entity: NamedEntityData,
        basis: Retrievable
    ): String {
        TODO("Not yet implemented")
    }

    override fun <T> loadEntity(type: Class<T>, id: String): T? {
        TODO("Not yet implemented")
    }

    override fun queryForEntities(
        purpose: String,
        query: String,
        params: Map<String, *>,
        logger: Logger?
    ): List<EntityData> {
        TODO("Not yet implemented")
    }

    override fun chunkSimilaritySearch(
        purpose: String,
        query: String,
        params: Map<String, *>,
        logger: Logger?
    ): List<SimilarityResult<out Chunk>> {
        TODO("Not yet implemented")
    }

    override fun entityDataSimilaritySearch(
        purpose: String,
        query: String,
        params: Map<String, *>,
        logger: Logger?
    ): List<SimilarityResult<out EntityData>> {
        TODO("Not yet implemented")
    }

    override fun chunkFullTextSearch(
        purpose: String,
        query: String,
        params: Map<String, *>,
        logger: Logger?
    ): List<SimilarityResult<out Chunk>> {
        TODO("Not yet implemented")
    }

    override fun entityFullTextSearch(
        purpose: String,
        query: String,
        params: Map<String, *>,
        logger: Logger?
    ): List<SimilarityResult<out EntityData>> {
        TODO("Not yet implemented")
    }

    override fun queryForInt(query: String, params: Map<String, *>): Int {
        TODO("Not yet implemented")
    }

    override fun query(
        purpose: String,
        query: String,
        params: Map<String, *>,
        logger: Logger?
    ): Result {
        TODO("Not yet implemented")
    }
}