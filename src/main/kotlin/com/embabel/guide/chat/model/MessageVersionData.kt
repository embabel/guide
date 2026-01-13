package com.embabel.guide.chat.model

import org.drivine.annotation.NodeFragment
import org.drivine.annotation.NodeId
import java.time.Instant

@NodeFragment(labels = ["MessageVersion"])
data class MessageVersionData(
    @NodeId val versionId: String,
    val createdAt: Instant?,
    val editorRole: String?,   // "user" | "assistant" | "system"
    val reason: String?,
    val text: String
)