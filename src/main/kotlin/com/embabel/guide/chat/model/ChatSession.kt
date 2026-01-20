package com.embabel.guide.chat.model

import com.embabel.guide.domain.GuideUser
import org.drivine.annotation.Direction
import org.drivine.annotation.GraphRelationship
import org.drivine.annotation.GraphView
import org.drivine.annotation.Root

/**
 * Chat session with messages.
 * Each message includes its current version text and author.
 * Messages are sorted by messageId (UUIDv7 = chronological order) via DSL deep sorting.
 */
@GraphView
data class ChatSession(
    @Root val session: ChatSessionData,

    @GraphRelationship(type = "OWNED_BY", direction = Direction.OUTGOING)
    val owner: GuideUser,

    @GraphRelationship(type = "HAS_MESSAGE", direction = Direction.OUTGOING)
    val messages: List<MessageWithVersion> = emptyList()
) {
    /** Returns a copy of this session with an additional message. */
    fun withMessage(message: MessageWithVersion): ChatSession =
        copy(messages = messages + message)
}
