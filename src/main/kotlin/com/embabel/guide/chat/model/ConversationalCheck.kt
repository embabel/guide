package com.embabel.guide.chat.model

/**
 * Result of nano-based classification of a user message.
 * If conversational, includes a quick response to avoid the full RAG pipeline.
 */
data class ConversationalCheck(
    val conversational: Boolean = false,
    val response: String? = null
)
