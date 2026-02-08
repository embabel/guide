package com.embabel.guide.chat.service

import com.embabel.chat.store.model.StoredUser
import com.embabel.guide.chat.model.StatusMessage
import com.embabel.guide.domain.GuideUserData
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.domain.GuideUserService
import com.embabel.guide.util.UUIDv7
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.drivine.manager.GraphObjectManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
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
    private val guideUserService: GuideUserService,
    private val guideUserRepository: GuideUserRepository,
    @Qualifier("neoGraphObjectManager") private val graphObjectManager: GraphObjectManager
) {
    private val logger = LoggerFactory.getLogger(JesseService::class.java)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Jesse's GuideUserData - initialized on startup
    private lateinit var jesseUser: GuideUserData

    companion object {
        const val JESSE_USER_ID = "bot:jesse"
        const val JESSE_SESSION_ID = "jesse-bot-session"
        const val JESSE_DISPLAY_NAME = "Jesse"
    }

    @EventListener(ApplicationReadyEvent::class)
    fun initializeJesse() {
        logger.info("Initializing Jesse bot")

        // Get or create Jesse as a GuideUser (SessionUser) for message authorship
        jesseUser = guideUserRepository.findById(JESSE_USER_ID)
            .map { it.core }
            .orElseGet {
                logger.info("Creating Jesse user in database")
                val jesse = GuideUserData(
                    id = JESSE_USER_ID,
                    displayName = JESSE_DISPLAY_NAME
                )
                graphObjectManager.save(jesse)
                jesse
            }

        presenceService.touch(JESSE_USER_ID, JESSE_SESSION_ID, "active")
        logger.info("Jesse bot is now online with ID: {}", JESSE_USER_ID)
    }

    @Scheduled(fixedRate = 30000) // Every 30 seconds
    fun maintainPresence() {
        presenceService.touch(JESSE_USER_ID, JESSE_SESSION_ID, "active")
    }

    /**
     * Get Jesse's StoredUser for use as agent in conversations.
     */
    fun getJesseUser(): StoredUser = jesseUser

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
     * Assistant responses are delivered via MessageEvent -> MessageEventListener -> WebSocket.
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

                // Get or create session (lazy creation)
                // Message persistence is handled by the chatbot via STORED conversations
                logger.info("[session={}] Getting or creating session", effectiveSessionId)
                val sessionResult = chatSessionService.getOrCreateSession(
                    sessionId = effectiveSessionId,
                    ownerId = guideUserId,
                    messageForTitle = message
                )
                val title = sessionResult.session.session.title
                if (sessionResult.created) {
                    logger.info("[session={}] Created new session with title: {}", effectiveSessionId, title)
                } else {
                    logger.info("[session={}] Added message to existing session", effectiveSessionId)
                }

                // Send message to RAG adapter - conversation history is auto-loaded by the chatbot
                logger.info("[session={}] Calling RAG adapter", effectiveSessionId)
                val response = ragAdapter.sendMessage(
                    threadId = effectiveSessionId,
                    message = message,
                    fromUserId = guideUserId
                ) { event ->
                    logger.debug("[session={}] RAG event for user {}: {}", effectiveSessionId, fromWebUserId, event)
                    sendStatusToUser(fromWebUserId, event)
                }
                logger.info("[session={}] RAG adapter returned response ({} chars)", effectiveSessionId, response.length)

                // Clear status now that response is complete
                sendStatusToUser(fromWebUserId, "")

                // Message persistence and WebSocket delivery are handled automatically
                // by the chatbot's STORED conversation factory (fires MessageEvent on persist)
            } catch (e: Exception) {
                logger.error("[session={}] Error processing message from webUser {}: {}", effectiveSessionId, fromWebUserId, e.message, e)
                sendStatusToUser(fromWebUserId, "Error processing your request")
                // Error messages are sent via status channel - no need to persist
            }
        }
    }
}
