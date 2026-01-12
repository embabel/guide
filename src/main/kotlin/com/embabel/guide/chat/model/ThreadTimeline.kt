package com.embabel.guide.chat.model

import org.drivine.annotation.Direction
import org.drivine.annotation.GraphRelationship
import org.drivine.annotation.GraphView
import org.drivine.annotation.Root

/**
 * Simplified thread timeline - direct Thread → Turn relationship.
 * Each turn includes its current version text and author.
 */
@GraphView
data class ThreadTimeline(
    @Root val thread: ThreadCore,

    @GraphRelationship(type = "HAS_TURN", direction = Direction.OUTGOING)
    val turns: List<TurnWithCurrentText>
)