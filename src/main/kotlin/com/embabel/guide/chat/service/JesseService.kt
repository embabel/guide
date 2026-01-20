package com.embabel.guide.chat.service

import com.embabel.guide.chat.model.DeliveredMessage
import com.embabel.guide.chat.model.MessageWithVersion
import com.embabel.guide.chat.model.StatusMessage
import com.embabel.guide.domain.GuideUserService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class JesseService(
    private val chatService: ChatService,
    private val presenceService: PresenceService,
    private val ragAdapter: RagServiceAdapter,
    private val chatSessionService: ChatSessionService,
    private val guideUserService: GuideUserService
) {
    private val logger = LoggerFactory.getLogger(JesseService::class.java)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val JESSE_USER_ID = "bot:jesse"
        const val JESSE_SESSION_ID = "jesse-bot-session"
    }

    @EventListener(ApplicationReadyEvent::class)
    fun initializeJesse() {
        logger.info("Initializing Jesse bot")
        presenceService.touch(JESSE_USER_ID, JESSE_SESSION_ID, "active")
        logger.info("Jesse bot is now online with ID: {}", JESSE_USER_ID)
    }

    @Scheduled(fixedRate = 30000) // Every 30 seconds
    fun maintainPresence() {
        presenceService.touch(JESSE_USER_ID, JESSE_SESSION_ID, "active")
    }

    private fun sendMessageToUser(toUserId: String, message: MessageWithVersion, sessionId: String) {
        logger.debug("Jesse sending message to user: {}", toUserId)
        val deliveredMessage = DeliveredMessage.createFrom(message, sessionId)
        chatService.sendToUser(toUserId, deliveredMessage)
        chatService.sendStatusToUser(toUserId, status = StatusMessage(fromUserId = JESSE_USER_ID))
    }

    private fun sendStatusToUser(toUserId: String, status: String) {
        logger.debug("Jesse sending status to user {}: {}", toUserId, status)
        val statusMessage = StatusMessage(
            fromUserId = JESSE_USER_ID,
            status = status
        )
        chatService.sendStatusToUser(toUserId, statusMessage)
    }

    /**
     * Receive a message from a user, persist it, get AI response, and send back.
     *
     * @param sessionId the session to add messages to
     * @param fromWebUserId the WebUser ID from the JWT principal
     * @param message the message text
     */
    fun receiveMessage(sessionId: String, fromWebUserId: String, message: String) {
        if (sessionId.isBlank()) {
            logger.warn("Received message with blank sessionId from webUser {} - front-end may be sending 'threadId' instead of 'sessionId'", fromWebUserId)
        }
        logger.info("[session={}] Jesse received message from webUser {}: '{}'", sessionId, fromWebUserId, message.take(100))

        coroutineScope.launch {
            try {
                logger.info("[session={}] Starting async processing for webUser {}", sessionId, fromWebUserId)

                // Look up the GuideUser by WebUser ID
                val guideUser = guideUserService.findByWebUserId(fromWebUserId).orElseThrow {
                    IllegalArgumentException("User not found for webUserId: $fromWebUserId")
                }
                val guideUserId = guideUser.core.id
                logger.info("[session={}] Found guideUser {} for webUser {}", sessionId, guideUserId, fromWebUserId)

                // Save the user's message to the session
                logger.info("[session={}] Saving user message to session", sessionId)
                chatSessionService.addMessage(
                    sessionId = sessionId,
                    text = message,
                    role = ChatSessionService.ROLE_USER,
                    authorId = guideUserId
                )
                logger.info("[session={}] User message saved", sessionId)

                // Load existing session messages for context
                val chatSession = chatSessionService.findBySessionId(sessionId).orElse(null)
                val priorMessages = chatSession?.messages
                    ?.dropLast(1)  // Exclude the message we just added
                    ?.map { PriorMessage(it.message.role, it.current.text) }
                    ?: emptyList()
                logger.info("[session={}] Loaded {} prior messages for context", sessionId, priorMessages.size)

                // Send status updates to the user while processing
                // Use WebUser ID for WebSocket delivery (that's the principal in the session)
                logger.info("[session={}] Calling RAG adapter", sessionId)
                val response = ragAdapter.sendMessage(
                    threadId = sessionId,
                    message = message,
                    fromUserId = guideUserId,
                    priorMessages = priorMessages
                ) { event ->
                    logger.debug("[session={}] RAG event for user {}: {}", sessionId, fromWebUserId, event)
                    sendStatusToUser(fromWebUserId, event)
                }
                logger.info("[session={}] RAG adapter returned response ({} chars)", sessionId, response.length)

                // Save the assistant's response to the session
                logger.info("[session={}] Saving assistant response to session", sessionId)
                val assistantMessage = chatSessionService.addMessage(
                    sessionId = sessionId,
                    text = response,
                    role = ChatSessionService.ROLE_ASSISTANT,
                    authorId = null  // System-generated response
                )
                logger.info("[session={}] Assistant message saved", sessionId)

                // Send the response to the user via WebSocket (use WebUser ID)
                logger.info("[session={}] Sending response to webUser {} via WebSocket", sessionId, fromWebUserId)
                sendMessageToUser(fromWebUserId, assistantMessage, sessionId)
                logger.info("[session={}] Response sent successfully", sessionId)
            } catch (e: Exception) {
                logger.error("[session={}] Error processing message from webUser {}: {}", sessionId, fromWebUserId, e.message, e)
                sendStatusToUser(fromWebUserId, "Error processing your request")

                // Save error message to session and send to user
                val errorMessage = chatSessionService.addMessage(
                    sessionId = sessionId,
                    text = "Sorry, I encountered an error while processing your message. Please try again!",
                    role = ChatSessionService.ROLE_ASSISTANT,
                    authorId = null
                )
                sendMessageToUser(fromWebUserId, errorMessage, sessionId)
            }
        }
    }
}
