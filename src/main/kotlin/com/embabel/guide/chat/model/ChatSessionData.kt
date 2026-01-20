package com.embabel.guide.chat.model

import org.drivine.annotation.NodeFragment
import org.drivine.annotation.NodeId
import java.time.Instant

@NodeFragment(labels = ["ChatSession"])
data class ChatSessionData(
    @NodeId val sessionId: String,
    val title: String?,
    val createdAt: Instant?
)