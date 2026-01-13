package com.embabel.guide.chat.model

import org.drivine.annotation.NodeFragment
import org.drivine.annotation.NodeId
import java.time.Instant

@NodeFragment(labels = ["Thread"])
data class ThreadData(
    @NodeId val threadId: String,
    val title: String?,
    val createdAt: Instant?
)