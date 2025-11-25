package com.embabel.agent.rag.neo.drivine

import com.embabel.agent.rag.model.Chunk
import com.embabel.agent.rag.model.ContentElement
import com.embabel.agent.rag.model.MaterializedDocument
import org.springframework.stereotype.Component

@Component
class ContentElementMapper {

    fun rowToContentElement(row: Map<*, *>): ContentElement {
        val metadata = mutableMapOf<String, Any>()
        metadata["source"] = row["metadata_source"] ?: "unknown"
        val labels = row["labels"] as? List<String> ?: error("Must have labels")
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