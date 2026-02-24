package com.embabel.guide.chat.service

import com.embabel.agent.api.channel.*
import com.embabel.chat.AssistantMessage
import com.embabel.chat.ChatSession
import com.embabel.chat.Chatbot
import com.embabel.chat.ChatTrigger
import com.embabel.chat.UserMessage
import com.embabel.guide.domain.GuideUserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Real implementation of RagServiceAdapter that integrates with the Guide chatbot.
 *
 * This adapter bridges the WebSocket chat system with the Guide's RAG-powered chatbot,
 * enabling real-time AI responses through the web interface.
 *
 * The chatbot uses STORED conversations, so message history is automatically loaded
 * when restoring a session by conversation ID.
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

    // Session cache to maintain AgentProcess continuity per thread
    private val threadSessions = ConcurrentHashMap<String, SessionContext>()

    companion object {
        private const val RESPONSE_TIMEOUT_MS = 120000 // 2 minutes
        private const val POLL_INTERVAL_MS = 500L
        private const val DEFAULT_ERROR_MESSAGE =
            "I received your message but had trouble generating a response. Please try again."
    }

    /**
     * Holds a chat session with a dynamic output channel that can be updated per message
     */
    private class SessionContext(
        val session: ChatSession,
        val dynamicChannel: DynamicOutputChannel
    )

    /**
     * Output channel wrapper that delegates to a current channel, allowing the delegate to be updated
     */
    private class DynamicOutputChannel : OutputChannel {
        @Volatile
        var currentDelegate: OutputChannel? = null

        override fun send(event: OutputChannelEvent) {
            currentDelegate?.send(event)
                ?: throw IllegalStateException("No output channel delegate set")
        }
    }

    override suspend fun sendMessage(
        threadId: String,
        message: String,
        fromUserId: String,
        onEvent: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        logger.info("[TRACE] sendMessage called: thread={}, user={}, message='{}'", threadId, fromUserId, message.take(100))

        val responseBuilder = StringBuilder()
        var isComplete = false

        // Create output channel for this specific message
        val messageOutputChannel = createOutputChannel(responseBuilder, onEvent) { isComplete = true }

        try {
            val guideUser = guideUserRepository.findById(fromUserId)
                .orElseThrow { RuntimeException("No user found with id: $fromUserId") }

            // Get or create session context for this thread
            // The chatbot uses STORED conversations with conversationId=threadId,
            // so message history is automatically loaded when restoring a session
            val sessionContext = threadSessions.computeIfAbsent(threadId) {
                logger.info("Creating/restoring chat session for thread: {} (user: {})", threadId, fromUserId)
                val dynamicChannel = DynamicOutputChannel()
                dynamicChannel.currentDelegate = messageOutputChannel
                val session = chatbot.createSession(guideUser, dynamicChannel, null, threadId)
                SessionContext(session, dynamicChannel)
            }

            // Update the dynamic channel to point to this message's output channel
            sessionContext.dynamicChannel.currentDelegate = messageOutputChannel

            // Process the message with the cached session (which maintains conversation history)
            logger.info("[TRACE] Calling onUserMessage: thread={}, message='{}'", threadId, message.take(100))
            sessionContext.session.onUserMessage(UserMessage(message))

            waitForResponse { isComplete }

            responseBuilder.toString().ifBlank { DEFAULT_ERROR_MESSAGE }
        } catch (e: Exception) {
            logger.error("Error processing message from user {} in thread {}: {}", fromUserId, threadId, e.message, e)
            // Invalidate the cached session since it may be in a bad state
            threadSessions.remove(threadId)
            throw e
        }
    }

    override suspend fun sendTrigger(
        threadId: String,
        trigger: ChatTrigger,
        onEvent: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        logger.info("Processing trigger for thread: {}", threadId)

        val responseBuilder = StringBuilder()
        var isComplete = false

        val messageOutputChannel = createOutputChannel(responseBuilder, onEvent) { isComplete = true }

        try {
            val user = trigger.onBehalfOf.firstOrNull()

            // Get or create session context for this thread
            val sessionContext = threadSessions.computeIfAbsent(threadId) {
                logger.info("Creating/restoring chat session for thread: {} (trigger for user: {})", threadId, user)
                val dynamicChannel = DynamicOutputChannel()
                dynamicChannel.currentDelegate = messageOutputChannel
                val session = chatbot.createSession(user, dynamicChannel, null, threadId)
                SessionContext(session, dynamicChannel)
            }

            sessionContext.dynamicChannel.currentDelegate = messageOutputChannel

            // Trigger — prompt is NOT stored in conversation
            sessionContext.session.onTrigger(trigger)

            waitForResponse { isComplete }

            responseBuilder.toString().ifBlank { DEFAULT_ERROR_MESSAGE }
        } catch (e: Exception) {
            logger.error("Error processing trigger in thread {}: {}", threadId, e.message, e)
            threadSessions.remove(threadId)
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
                logger.info("[TRACE] OutputChannel received AssistantMessage: '{}'", msg.content.take(100))
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
