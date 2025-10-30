package com.embabel.guide.chat.model

import java.time.Instant
import java.util.UUID

data class StatusMessage(
    val id: String = UUID.randomUUID().toString(),
    val fromUserId: String,
    val status: String? = null,
    val ts: Instant = Instant.now()
)
