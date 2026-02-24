package com.embabel.guide.chat.model

import java.time.Instant

/**
 * Pushed to the client when a session is created or updated on the backend.
 * Allows the frontend to keep its session list in sync without polling.
 */
data class SessionEvent(
    val sessionId: String,
    val title: String? = null,
    val type: String = "created",
    val ts: Instant = Instant.now(),
)
