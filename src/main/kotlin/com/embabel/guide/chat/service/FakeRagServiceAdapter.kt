package com.embabel.guide.chat.service

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Fake implementation of RagServiceAdapter for development and testing.
 *
 * This implementation simulates RAG processing with realistic delays and status events.
 * It can be replaced with a real RAG implementation by setting the appropriate Spring profile.
 */
@Service
@ConditionalOnProperty(
    name = ["rag.adapter.type"],
    havingValue = "fake",
    matchIfMissing = true // Use fake by default
)
class FakeRagServiceAdapter : RagServiceAdapter {
    private val logger = LoggerFactory.getLogger(FakeRagServiceAdapter::class.java)

    override suspend fun sendMessage(
        threadId: String,
        message: String,
        fromUserId: String,
        onEvent: (String) -> Unit
    ): String {
        logger.info("Processing fake RAG request from user: {} in thread: {}", fromUserId, threadId)

        // Simulate processing stages with events and minimal delays for testing
        onEvent("Analyzing your question...")
        delay(10)

        onEvent("Searching knowledge base...")
        delay(10)

        onEvent("Retrieving relevant documents...")
        delay(10)

        onEvent("Generating response...")
        delay(10)

        // Generate response based on message content (moved from JesseService)
        val response = when {
            message.lowercase().contains("hello") -> {
                "Hello! I'm Jesse, your AI assistant. How can I help you today?"
            }
            message.lowercase().contains("help") -> {
                "I'm here to help! You can ask me questions and I'll do my best to assist you. " +
                "I have access to a knowledge base and can help with various topics."
            }
            message.lowercase().contains("status") -> {
                "I'm online and ready to help! My RAG system is fully operational. What would you like to know?"
            }
            message.lowercase().contains("weather") -> {
                "I don't have real-time weather data access in this demo, but I'd be happy to help " +
                "with other questions! Once connected to live data sources, I could provide weather updates."
            }
            message.lowercase().contains("time") -> {
                "I don't have access to real-time data in this demo version, but I can help with " +
                "many other topics from my knowledge base!"
            }
            message.length < 5 -> {
                "Could you provide a bit more detail? I'm here to help with any questions you might have!"
            }
            else -> {
                "I received your message: \"$message\". " +
                "In a real RAG system, I would search through documents and knowledge bases to provide " +
                "you with accurate, contextual information. For now, I'm using simulated responses, " +
                "but I'm ready to help however I can!"
            }
        }

        logger.debug("Fake RAG response generated for user: {}", fromUserId)
        return response
    }

    override suspend fun generateTitle(content: String, fromUserId: String): String {
        logger.debug("Generating fake title for content from user: {}", fromUserId)
        // Return first few words of content as title, or a default
        val words = content.trim().split("\\s+".toRegex())
        return if (words.size <= 4) {
            content.take(50)
        } else {
            words.take(4).joinToString(" ") + "..."
        }
    }
}
