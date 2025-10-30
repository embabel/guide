package com.embabel.guide.chat.model

import java.time.Instant
import java.util.UUID

data class DeliveredMessage(
    val id: String = UUID.randomUUID().toString(),
    val fromUserId: String,
    val toUserId: String? = null,
    val room: String? = null,
    val body: String,
    val ts: Instant = Instant.now()
)
