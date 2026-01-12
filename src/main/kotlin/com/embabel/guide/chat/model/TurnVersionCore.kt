package com.embabel.guide.chat.model

import org.drivine.annotation.NodeFragment
import org.drivine.annotation.NodeId
import java.time.Instant

@NodeFragment(labels = ["TurnVersion"])
data class TurnVersionCore(
    @NodeId val versionId: String,
    val createdAt: Instant?,
    val editorRole: String?,   // "user" | "assistant" | "system"
    val reason: String?,
    val text: String
)
