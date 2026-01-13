package com.embabel.guide.chat.model

import com.embabel.guide.domain.GuideUser
import org.drivine.annotation.Direction
import org.drivine.annotation.GraphRelationship
import org.drivine.annotation.GraphView
import org.drivine.annotation.Root

/**
 * Thread timeline with messages.
 * Each message includes its current version text and author.
 */
@GraphView
data class ThreadTimeline(
    @Root val thread: ThreadData,

    @GraphRelationship(type = "OWNED_BY", direction = Direction.OUTGOING)
    val owner: GuideUser,

    @GraphRelationship(type = "HAS_MESSAGE", direction = Direction.OUTGOING)
    val messages: List<MessageWithVersion>
)