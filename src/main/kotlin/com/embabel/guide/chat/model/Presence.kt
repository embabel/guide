package com.embabel.guide.chat.model

import java.time.Instant

data class Presence(
    val userId: String,
    val sessionId: String,
    var lastSeen: Instant = Instant.now(),
    var status: String = "active"
)
