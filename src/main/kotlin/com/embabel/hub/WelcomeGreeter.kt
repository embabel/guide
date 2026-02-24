package com.embabel.hub

import com.embabel.guide.chat.model.DeliveredMessage
import com.embabel.guide.chat.service.ChatService
import com.embabel.guide.chat.service.ChatSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Interface for greeting new users with a welcome message.
 */
interface WelcomeGreeter {
    /**
     * Greet a new user by creating a welcome session and sending the message.
     * This is a fire-and-forget operation that runs asynchronously.
     *
     * WebSocket delivery is handled by [com.embabel.guide.chat.event.MessageEventListener]
     * when the chatbot persists the response via StoredConversation.
     *
     * @param guideUserId the GuideUser's core ID (owner of the session)
     * @param webUserId the WebUser's ID (for WebSocket delivery)
     * @param displayName the user's display name for personalized greeting
     */
    fun greetNewUser(guideUserId: String, webUserId: String, displayName: String)
}

/**
 * Production implementation that creates AI-generated welcome sessions.
 */
@Component
class WelcomeGreeterImpl(
    private val chatSessionService: ChatSessionService,
    private val chatService: ChatService
) : WelcomeGreeter {

    private val logger = LoggerFactory.getLogger(WelcomeGreeterImpl::class.java)

    override fun greetNewUser(guideUserId: String, webUserId: String, displayName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                chatSessionService.createWelcomeSession(
                    ownerId = guideUserId,
                    displayName = displayName
                )
                // WebSocket delivery is handled by MessageEventListener via StoredConversation events
            } catch (e: Exception) {
                logger.error("Failed to create AI welcome session for user {}, falling back to static message: {}", guideUserId, e.message, e)
                try {
                    val fallbackSession = chatSessionService.createWelcomeSessionWithMessage(ownerId = guideUserId)
                    val fallbackMessage = fallbackSession.messages.firstOrNull()
                    if (fallbackMessage != null) {
                        val delivered = DeliveredMessage.createFrom(fallbackMessage, fallbackSession.session.sessionId, fallbackSession.session.title)
                        chatService.sendToUser(webUserId, delivered)
                    }
                } catch (fallbackError: Exception) {
                    logger.error("Failed to create fallback welcome session for user {}: {}", guideUserId, fallbackError.message, fallbackError)
                }
            }
        }
    }
}
