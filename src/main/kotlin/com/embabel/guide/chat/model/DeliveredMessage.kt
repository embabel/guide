package com.embabel.guide.chat.model

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
    val authorId: String? = null
) {
    companion object {
        fun createFrom(msg: MessageWithVersion, sessionId: String): DeliveredMessage {
            return DeliveredMessage(
                id = msg.message.messageId,
                sessionId = sessionId,
                role = msg.message.role,
                body = msg.current.text,
                ts = msg.message.createdAt ?: Instant.now(),
                authorId = msg.authoredBy?.core?.id
            )
        }
    }
}
