package com.embabel.guide.chat.model

enum class MessageCategory {
    CONVERSATIONAL,
    COMMAND,
    INFORMATIONAL,
}

/**
 * Result of nano-based classification of a user message.
 * If conversational, includes a quick response to avoid the full RAG pipeline.
 */
data class CategoryCheck(
    val category: MessageCategory = MessageCategory.INFORMATIONAL,
    val response: String? = null,
)
