package com.embabel.agent.rag.neo.drivine.mappers

import com.embabel.agent.rag.model.Chunk
import com.embabel.common.core.types.SimilarityResult
import com.embabel.common.core.types.SimpleSimilaritySearchResult
import org.drivine.mapper.RowMapper
import org.springframework.stereotype.Component

@Component
class ChunkSimilarityMapper : RowMapper<SimilarityResult<Chunk>> {

    override fun map(row: Map<String, *>): SimilarityResult<Chunk> {
        return SimpleSimilaritySearchResult(
            match = Chunk(
                id = row["id"] as String,
                text = row["text"] as String,
                parentId = row["parentId"] as? String ?: "unknown",
                metadata = (row["metadata"] as? Map<String, Any>) ?: emptyMap(),
            ),
            score = row["score"] as Double,
        )
    }
}