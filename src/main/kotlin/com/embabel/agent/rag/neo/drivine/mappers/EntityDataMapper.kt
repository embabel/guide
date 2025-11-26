package com.embabel.agent.rag.neo.drivine.mappers

import com.embabel.agent.rag.model.EntityData
import com.embabel.agent.rag.model.SimpleNamedEntityData
import org.drivine.mapper.RowMapper
import org.springframework.stereotype.Component

@Component
class EntityDataMapper : RowMapper<EntityData> {

    override fun map(row: Map<String, *>): EntityData {
        return SimpleNamedEntityData(
            id = row["id"] as String,
            name = row["name"] as String,
            description = row["description"] as String? ?: "",
            labels = (row["labels"] as? List<*>)?.map { it.toString() }?.toSet() ?: emptySet(),
            properties = emptyMap(),
        )
    }
}