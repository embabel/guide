package com.embabel.guide.chat.listener

import com.embabel.guide.chat.service.JesseService
import com.embabel.guide.domain.drivine.DrivineGuideUserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionConnectedEvent

/**
 * Listens for WebSocket connection events and sends greetings to connected users.
 */
@Component
class WebSocketConnectionListener(
    private val jesseService: JesseService,
    private val guideUserRepository: DrivineGuideUserRepository
) {

    private val logger = LoggerFactory.getLogger(WebSocketConnectionListener::class.java)

    @EventListener
    fun handleWebSocketConnectListener(event: SessionConnectedEvent) {
        val headerAccessor = StompHeaderAccessor.wrap(event.message)
        val userId = headerAccessor.user?.name

        if (userId != null) {
            logger.info("User connected: $userId")

            // Try to find the user to get their display name
            val webUser = guideUserRepository.findByWebUserId(userId).orElse(null)

            if (webUser != null) {
                // Use the User interface methods directly from the composite
                val displayName = webUser.displayName
                val username = webUser.username
                logger.info("Sending greeting to authenticated user: $displayName (username: $username)")
                val greetingMessage = "User $displayName has come online, and would like a greeting"
                jesseService.receiveMessage(userId, greetingMessage)

            } else {
                logger.warn("Could not find user with ID: $userId")
            }
        }
    }
}
