package com.embabel.guide.chat.listener

import com.embabel.guide.chat.service.JesseService
import com.embabel.guide.domain.GuideUserRepository
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
    private val guideUserRepository: GuideUserRepository
) {

    private val logger = LoggerFactory.getLogger(WebSocketConnectionListener::class.java)

    @EventListener
    fun handleWebSocketConnectListener(event: SessionConnectedEvent) {
        val headerAccessor = StompHeaderAccessor.wrap(event.message)
        val userId = headerAccessor.user?.name

        if (userId != null) {
            logger.info("User connected: $userId")
        }
    }
}
