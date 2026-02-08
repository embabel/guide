package com.embabel.guide.chat.model

import com.embabel.chat.store.model.SimpleStoredMessage
import java.time.Instant

/**
 * Message delivered to a client, mapped from the persistent model.
 */
data class DeliveredMessage(
    val id: String,
    val sessionId: String,
    val role: String,
    val body: String,
    val ts: Instant,
    val authorId: String? = null,
    val title: String? = null
) {
    companion object {
        fun createFrom(msg: SimpleStoredMessage, sessionId: String, title: String? = null): DeliveredMessage {
            return DeliveredMessage(
                id = msg.messageId,
                sessionId = sessionId,
                role = msg.role.name.lowercase(),
                body = msg.content,
                ts = msg.message.createdAt,
                authorId = msg.author?.id,
                title = title
            )
        }
    }
}
