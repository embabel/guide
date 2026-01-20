package com.embabel.guide.chat.model

/**
 * Incoming chat message from a client.
 * Default values required for STOMP message converter deserialization.
 */
data class ChatMessage(
    val sessionId: String = "",
    val body: String = ""
)
