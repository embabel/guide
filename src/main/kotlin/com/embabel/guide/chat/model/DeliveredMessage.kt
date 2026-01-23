package com.embabel.guide.chat.model

import com.embabel.chat.store.model.StoredMessage
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
        fun createFrom(msg: StoredMessage, sessionId: String, title: String? = null): DeliveredMessage {
            return DeliveredMessage(
                id = msg.messageId,
                sessionId = sessionId,
                role = msg.role,
                body = msg.content,
                ts = msg.createdAt,
                authorId = msg.author?.id,
                title = title
            )
        }
    }
}
