package com.embabel.hub

import com.embabel.guide.chat.model.DeliveredMessage
import com.embabel.guide.chat.service.ChatService
import com.embabel.guide.chat.service.ChatSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component

/**
 * Interface for greeting new users with a welcome message.
 */
interface WelcomeGreeter {
    /**
     * Greet a new user by creating a welcome session and sending the message.
     * This is a fire-and-forget operation that runs asynchronously.
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

    override fun greetNewUser(guideUserId: String, webUserId: String, displayName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val chatSession = chatSessionService.createWelcomeSession(
                ownerId = guideUserId,
                displayName = displayName
            )
            // Send the welcome message to the user via WebSocket
            val welcomeMessage = chatSession.messages.firstOrNull()
            if (welcomeMessage != null) {
                val delivered = DeliveredMessage.createFrom(welcomeMessage, chatSession.session.sessionId)
                chatService.sendToUser(webUserId, delivered)
            }
        }
    }
}
