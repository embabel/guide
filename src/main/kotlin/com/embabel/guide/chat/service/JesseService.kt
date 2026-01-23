package com.embabel.guide.chat.service

import com.embabel.chat.store.model.StoredMessage
import com.embabel.guide.chat.model.DeliveredMessage
import com.embabel.guide.chat.model.StatusMessage
import com.embabel.guide.domain.GuideUserService
import com.embabel.guide.util.UUIDv7
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

    private fun sendMessageToUser(toUserId: String, message: StoredMessage, sessionId: String, title: String? = null) {
        logger.debug("Jesse sending message to user: {}", toUserId)
        val deliveredMessage = DeliveredMessage.createFrom(message, sessionId, title)
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
     * Creates the session lazily if it doesn't exist.
     *
     * @param sessionId the session to add messages to, or blank/empty to create a new session
     * @param fromWebUserId the WebUser ID from the JWT principal
     * @param message the message text
     */
    fun receiveMessage(sessionId: String, fromWebUserId: String, message: String) {
        // Generate new sessionId if not provided (new session)
        val isNewSession = sessionId.isBlank()
        val effectiveSessionId = if (isNewSession) {
            UUIDv7.generateString().also {
                logger.info("Generated new sessionId {} for webUser {}", it, fromWebUserId)
            }
        } else {
            sessionId
        }
        logger.info("[session={}] Jesse received message from webUser {}: '{}'", effectiveSessionId, fromWebUserId, message.take(100))

        coroutineScope.launch {
            try {
                logger.info("[session={}] Starting async processing for webUser {}", effectiveSessionId, fromWebUserId)

                // Notify user if we're creating a new session (title generation takes time)
                if (isNewSession) {
                    sendStatusToUser(fromWebUserId, "Creating new conversation...")
                }

                // Look up the GuideUser by WebUser ID (set up during WebSocket handshake)
                val guideUser = guideUserService.findByWebUserId(fromWebUserId).orElseThrow {
                    IllegalArgumentException("User not found for webUserId: $fromWebUserId")
                }
                val guideUserId = guideUser.core.id
                logger.info("[session={}] Found guideUser {} for webUser {}", effectiveSessionId, guideUserId, fromWebUserId)

                // Get or create session with the user's message (lazy creation)
                logger.info("[session={}] Getting or creating session with user message", effectiveSessionId)
                val sessionResult = chatSessionService.getOrCreateSessionWithMessage(
                    sessionId = effectiveSessionId,
                    ownerId = guideUserId,
                    message = message,
                    authorId = guideUserId
                )
                val title = sessionResult.session.session.title
                if (sessionResult.created) {
                    logger.info("[session={}] Created new session with title: {}", effectiveSessionId, title)
                } else {
                    logger.info("[session={}] Added message to existing session", effectiveSessionId)
                }

                // Load existing session messages for context (exclude the message we just added)
                val priorMessages = sessionResult.session.messages
                    .dropLast(1)
                    .map { PriorMessage(it.role, it.content) }
                logger.info("[session={}] Loaded {} prior messages for context", effectiveSessionId, priorMessages.size)

                // Send status updates to the user while processing
                logger.info("[session={}] Calling RAG adapter", effectiveSessionId)
                val response = ragAdapter.sendMessage(
                    threadId = effectiveSessionId,
                    message = message,
                    fromUserId = guideUserId,
                    priorMessages = priorMessages
                ) { event ->
                    logger.debug("[session={}] RAG event for user {}: {}", effectiveSessionId, fromWebUserId, event)
                    sendStatusToUser(fromWebUserId, event)
                }
                logger.info("[session={}] RAG adapter returned response ({} chars)", effectiveSessionId, response.length)

                // Save the assistant's response to the session
                logger.info("[session={}] Saving assistant response to session", effectiveSessionId)
                val assistantMessage = chatSessionService.addMessage(
                    sessionId = effectiveSessionId,
                    text = response,
                    role = ChatSessionService.ROLE_ASSISTANT,
                    authorId = null  // System-generated response
                )
                logger.info("[session={}] Assistant message saved", effectiveSessionId)

                // Send the response to the user via WebSocket (include title for new sessions)
                logger.info("[session={}] Sending response to webUser {} via WebSocket", effectiveSessionId, fromWebUserId)
                sendMessageToUser(fromWebUserId, assistantMessage, effectiveSessionId, title)
                logger.info("[session={}] Response sent successfully", effectiveSessionId)
            } catch (e: Exception) {
                logger.error("[session={}] Error processing message from webUser {}: {}", effectiveSessionId, fromWebUserId, e.message, e)
                sendStatusToUser(fromWebUserId, "Error processing your request")

                // Try to save error message to session - may fail if session wasn't created
                try {
                    val errorMessage = chatSessionService.addMessage(
                        sessionId = effectiveSessionId,
                        text = "Sorry, I encountered an error while processing your message. Please try again!",
                        role = ChatSessionService.ROLE_ASSISTANT,
                        authorId = null
                    )
                    sendMessageToUser(fromWebUserId, errorMessage, effectiveSessionId)
                } catch (e2: Exception) {
                    logger.error("[session={}] Failed to save error message: {}", effectiveSessionId, e2.message)
                }
            }
        }
    }
}
