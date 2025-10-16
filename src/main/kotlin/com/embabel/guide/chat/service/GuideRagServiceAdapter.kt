package com.embabel.guide.chat.service

import com.embabel.agent.channel.*
import com.embabel.chat.AssistantMessage
import com.embabel.chat.Chatbot
import com.embabel.chat.UserMessage
import com.embabel.guide.domain.GuideUserRepository
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
    private val chatbot: Chatbot,
    private val guideUserRepository: GuideUserRepository
) : RagServiceAdapter {

    private val logger = LoggerFactory.getLogger(GuideRagServiceAdapter::class.java)

    companion object {
        private const val RESPONSE_TIMEOUT_MS = 120000 // 2 minutes
        private const val POLL_INTERVAL_MS = 500L
        private const val DEFAULT_ERROR_MESSAGE =
            "I received your message but had trouble generating a response. Please try again."
    }

    override suspend fun sendMessage(
        message: String,
        fromUserId: String,
        onEvent: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        logger.info("Processing Guide RAG request from user: {}", fromUserId)

        val responseBuilder = StringBuilder()
        var isComplete = false

        val outputChannel = createOutputChannel(responseBuilder, onEvent) { isComplete = true }

        try {
            val webUser = guideUserRepository.findById(fromUserId)
                .orElseThrow { RuntimeException("No user found with id: $fromUserId") }
            val session = chatbot.createSession(webUser, outputChannel, null)

            session.onUserMessage(UserMessage(message))

            waitForResponse { isComplete }

            responseBuilder.toString().ifBlank { DEFAULT_ERROR_MESSAGE }
        } catch (e: Exception) {
            logger.error("Error processing message from user {}: {}", fromUserId, e.message, e)
            throw e
        }
    }

    /**
     * Creates an output channel that captures chatbot events and responses.
     */
    private fun createOutputChannel(
        responseBuilder: StringBuilder,
        onEvent: (String) -> Unit,
        onComplete: () -> Unit
    ): OutputChannel = object : OutputChannel {
        override fun send(event: OutputChannelEvent) {
            logger.debug("OutputChannel received event: {}", event)

            when (event) {
                is MessageOutputChannelEvent -> handleMessageEvent(event, responseBuilder, onComplete)
                is ProgressOutputChannelEvent -> onEvent(event.message)
                is LoggingOutputChannelEvent -> logger.debug("Logging event: {}", event.message)
                else -> logger.debug("Unknown event type: {}", event)
            }
        }
    }

    /**
     * Handles message events from the chatbot output channel.
     */
    private fun handleMessageEvent(
        event: MessageOutputChannelEvent,
        responseBuilder: StringBuilder,
        onComplete: () -> Unit
    ) {
        when (val msg = event.message) {
            is AssistantMessage -> {
                responseBuilder.append(msg.content)
                onComplete()
            }
            else -> logger.debug("Received non-assistant message: {}", msg)
        }
    }

    /**
     * Waits for the chatbot response with a timeout.
     */
    private suspend fun waitForResponse(isComplete: () -> Boolean) {
        var waited = 0
        while (!isComplete() && waited < RESPONSE_TIMEOUT_MS) {
            delay(POLL_INTERVAL_MS)
            waited += POLL_INTERVAL_MS.toInt()
        }
    }
}
