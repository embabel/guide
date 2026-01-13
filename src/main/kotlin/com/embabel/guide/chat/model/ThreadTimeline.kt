package com.embabel.guide.chat.model

import com.embabel.guide.domain.GuideUser
import org.drivine.annotation.Direction
import org.drivine.annotation.GraphRelationship
import org.drivine.annotation.GraphView
import org.drivine.annotation.Root

/**
 * Thread timeline with messages.
 * Each message includes its current version text and author.
 * Messages are sorted by messageId (UUIDv7 = chronological order).
 */
@GraphView
data class ThreadTimeline(
    @Root val thread: ThreadData,

    @GraphRelationship(type = "OWNED_BY", direction = Direction.OUTGOING)
    val owner: GuideUser,

    @GraphRelationship(type = "HAS_MESSAGE", direction = Direction.OUTGOING)
    private val _messages: List<MessageWithVersion>
) {
    /** Messages sorted by messageId (UUIDv7 = chronological order). Sorted once on first access. */
    val messages: List<MessageWithVersion> by lazy { _messages.sortedBy { it.message.messageId } }
}