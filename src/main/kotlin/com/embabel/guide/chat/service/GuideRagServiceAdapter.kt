package com.embabel.guide.chat.service

import com.embabel.agent.channel.*
import com.embabel.chat.AssistantMessage
import com.embabel.chat.Chatbot
import com.embabel.chat.UserMessage
import com.embabel.guide.domain.WebUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Real implementation of RagServiceAdapter that integrates with the Guide chatbot.
 *
 * This adapter bridges the WebSocket chat system with the Guide's RAG-powered chatbot,
 * enabling real-time AI responses through the web interface.
 */
@Service
@ConditionalOnProperty(
    name = ["rag.adapter.type"],
    havingValue = "guide",
    matchIfMissing = false
)
class GuideRagServiceAdapter(
    private val chatbot: Chatbot
) : RagServiceAdapter {

    private val logger = LoggerFactory.getLogger(GuideRagServiceAdapter::class.java)

    override suspend fun sendMessage(
        message: String,
        fromUserId: String,
        onEvent: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        logger.info("Processing Guide RAG request from user: {}", fromUserId)

        val responseBuilder = StringBuilder()
        var isComplete = false

        // Create an output channel that captures events and responses
        val outputChannel = object : OutputChannel {
            override fun send(event: OutputChannelEvent) {
                logger.debug("OutputChannel received event: {}", event)

                when (event) {
                    is MessageOutputChannelEvent -> {
                        // Extract actual message content
                        when (val msg = event.message) {
                            is AssistantMessage -> {
                                responseBuilder.append(msg.content)
                                isComplete = true
                            }
                            else -> {
                                logger.debug("Received non-assistant message: {}", msg)
                            }
                        }
                    }
                    is ProgressOutputChannelEvent -> {
                        // Send progress updates to the UI
                        onEvent(event.message)
                    }
                    is LoggingOutputChannelEvent -> {
                        // Ignore logging events for now
                        logger.debug("Logging event: {}", event.message)
                    }
                    else -> {
                        logger.debug("Unknown event type: {}", event)
                    }
                }
            }
        }

        try {
            // Create a web user for this session
            val webUser = WebUser(fromUserId, null, null, null)

            // Create a chat session with the Guide chatbot
            val session = chatbot.createSession(webUser, outputChannel, null)

            // Send the user's message to the chatbot
            session.onUserMessage(UserMessage(message))

            // Wait for the response with a timeout
            var waited = 0
            while (!isComplete && waited < 120000) { // 2 minute timeout
                delay(500)
                waited += 500
            }

            val response = responseBuilder.toString()
            if (response.isBlank()) {
                "I received your message but had trouble generating a response. Please try again."
            } else {
                response
            }
        } catch (e: Exception) {
            logger.error("Error processing message from user {}: {}", fromUserId, e.message, e)
            throw e
        }
    }
}
