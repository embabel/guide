package com.embabel.guide.chat.model

/**
 * Client acknowledgment that a message was received.
 * Default values required for STOMP message converter deserialization.
 */
data class MessageAck(
    val messageId: String = ""
)