package com.embabel.guide.chat.event

import com.embabel.chat.event.MessageEvent
import com.embabel.guide.chat.model.DeliveredMessage
import com.embabel.guide.chat.model.StatusMessage
import com.embabel.guide.chat.service.ChatService
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.util.UUIDv7
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Listens for MessageEvents and delivers messages to users via WebSocket.
 *
 * This decouples message persistence from WebSocket delivery:
 * - ADDED: Message was added to conversation - send to recipient immediately
 * - PERSISTENCE_FAILED: Log error for monitoring
 */
@Component
class MessageEventListener(
    private val chatService: ChatService,
    private val guideUserRepository: GuideUserRepository
) {
    private val logger = LoggerFactory.getLogger(MessageEventListener::class.java)

    @EventListener(condition = "#event.status.name() == 'ADDED'")
    fun onMessageAdded(event: MessageEvent) {
        val toGuideUserId = event.toUserId
        if (toGuideUserId == null) {
            logger.debug("MessageEvent has no toUserId, skipping WebSocket delivery for session {}", event.conversationId)
            return
        }

        val message = event.message
        if (message == null) {
            logger.warn("MessageEvent ADDED has no message for session {}", event.conversationId)
            return
        }

        // Look up the GuideUser to get their webUserId for WebSocket routing
        val guideUser = guideUserRepository.findById(toGuideUserId).orElse(null)
        if (guideUser == null) {
            logger.warn("GuideUser not found for id {}, skipping WebSocket delivery", toGuideUserId)
            return
        }

        val webUserId = guideUser.webUser?.id
        if (webUserId == null) {
            logger.debug("GuideUser {} has no webUser, skipping WebSocket delivery", toGuideUserId)
            return
        }

        logger.debug("Delivering message to webUser {} (guideUser {}) for session {}",
            webUserId, toGuideUserId, event.conversationId)

        val delivered = DeliveredMessage(
            id = UUIDv7.generateString(),
            sessionId = event.conversationId,
            role = message.role.name.lowercase(),
            body = message.content,
            ts = event.timestamp,
            authorId = event.fromUserId,
            title = event.title
        )

        chatService.sendToUser(webUserId, delivered)

        // Send status update to clear typing indicator
        event.fromUserId?.let { fromUserId ->
            chatService.sendStatusToUser(webUserId, StatusMessage(fromUserId = fromUserId))
        }
    }

    @EventListener(condition = "#event.status.name() == 'PERSISTENCE_FAILED'")
    fun onPersistenceFailed(event: MessageEvent) {
        logger.error(
            "Message persistence failed for session {}, role={}, error={}",
            event.conversationId,
            event.role,
            event.error?.message,
            event.error
        )
        // Could notify user of failure, implement retry logic, etc.
    }
}