package com.embabel.guide.domain

import org.drivine.annotation.Direction
import org.drivine.annotation.GraphRelationship
import org.drivine.annotation.GraphView
import org.drivine.annotation.Root

/**
 * GraphView for a Persona node owned by a GuideUser.
 * System personas are owned by bot:jesse; user-defined personas are owned by the user.
 */
@GraphView
data class PersonaView(
    @Root
    val persona: PersonaData,

    @GraphRelationship(type = "OWNED_BY", direction = Direction.OUTGOING)
    val owner: GuideUserData,
)