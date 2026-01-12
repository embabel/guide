package com.embabel.guide.chat.model

import org.drivine.annotation.NodeFragment
import org.drivine.annotation.NodeId
import java.time.Instant

@NodeFragment(labels = ["Turn"])
data class TurnCore(
    @NodeId val turnId: String,
    val threadId: String,
    val role: String,          // "user" | "assistant" | "tool"
    val createdAt: Instant?,
    val isVariant: Boolean? = null,
    val variantKey: String? = null,
    val experimentId: String? = null
)
