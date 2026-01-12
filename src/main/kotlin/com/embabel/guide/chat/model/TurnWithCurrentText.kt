package com.embabel.guide.chat.model

import com.embabel.guide.domain.GuideUser
import org.drivine.annotation.Direction
import org.drivine.annotation.GraphRelationship
import org.drivine.annotation.GraphView
import org.drivine.annotation.Root

@GraphView
data class TurnWithCurrentText(
    @Root val turn: TurnCore,

    @GraphRelationship(type = "CURRENT", direction = Direction.OUTGOING)
    val current: TurnVersionCore,

    @GraphRelationship(type = "AUTHORED_BY", direction = Direction.OUTGOING)
    val authoredBy: GuideUser?
)
