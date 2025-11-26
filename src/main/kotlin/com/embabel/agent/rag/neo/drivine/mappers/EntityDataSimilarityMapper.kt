package com.embabel.agent.rag.neo.drivine.mappers

import com.embabel.agent.rag.model.EntityData
import com.embabel.agent.rag.model.SimpleEntityData
import com.embabel.agent.rag.model.SimpleNamedEntityData
import com.embabel.common.core.types.SimilarityResult
import com.embabel.common.core.types.SimpleSimilaritySearchResult
import org.drivine.mapper.RowMapper
import org.springframework.stereotype.Component

@Component
class EntityDataSimilarityMapper : RowMapper<SimilarityResult<EntityData>> {

    override fun map(row: Map<String, *>): SimilarityResult<EntityData> {
        val name = row["name"] as? String
        val description = row["description"] as? String
        val labels = (row["labels"] as? List<*>)?.map { it.toString() }?.toSet() ?: emptySet()
        val properties = row["properties"] as? Map<String, Any> ?: emptyMap()
        val match = if (name != null && description != null) {
            SimpleNamedEntityData(
                id = row["id"] as String,
                name = name,
                description = description,
                labels = labels,
                properties = properties,
            )
        } else {
            SimpleEntityData(
                id = row["id"] as String,
                labels = labels,
                properties = properties,
            )
        }
        return SimpleSimilaritySearchResult(
            match = match,
            score = row["score"] as Double,
        )
    }
}