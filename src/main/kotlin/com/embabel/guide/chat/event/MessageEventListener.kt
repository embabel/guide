package com.embabel.guide.chat.event

import com.embabel.chat.event.MessageEvent
import com.embabel.chat.store.repository.ChatSessionRepository
import com.embabel.guide.chat.model.DeliveredMessage
import com.embabel.guide.chat.model.SessionEvent
import com.embabel.guide.chat.model.StatusMessage
import com.embabel.guide.chat.service.ChatService
import com.embabel.guide.chat.service.MessageDeliveryService
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.narrator.NarrationCache
import com.embabel.chat.store.util.UUIDv7
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Listens for MessageEvents and delivers messages to users via WebSocket.
 *
 * This decouples message persistence from WebSocket delivery:
 * - ADDED: Message was added to conversation — deliver with retry until acknowledged
 * - PERSISTED: Message was saved to DB — persist narration alongside it
 * - PERSISTENCE_FAILED: Log error for monitoring
 */
@Component
class MessageEventListener(
    private val chatService: ChatService,
    private val messageDeliveryService: MessageDeliveryService,
    private val guideUserRepository: GuideUserRepository,
    private val narrationCache: NarrationCache,
    private val chatSessionRepository: ChatSessionRepository
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

        // Include narration from cache if available (computed by ChatActions)
        val narration = narrationCache.consumeForDelivery(event.conversationId)
        logger.info("[NARRATION] ADDED event for session {}, role={}, narration={}",
            event.conversationId, message.role, if (narration != null) "${narration.length} chars" else "NULL")

        val delivered = DeliveredMessage(
            id = UUIDv7.generateString(),
            sessionId = event.conversationId,
            role = message.role.name.lowercase(),
            body = message.content,
            ts = event.timestamp,
            authorId = event.fromUserId,
            title = event.title,
            narration = narration
        )
        logger.info("[NARRATION] DeliveredMessage narration={}", if (delivered.narration != null) "${delivered.narration.length} chars" else "NULL")

        // Push session event so the frontend can update its session list
        if (event.title != null) {
            chatService.sendSessionToUser(webUserId, SessionEvent(
                sessionId = event.conversationId,
                title = event.title,
            ))
        }

        messageDeliveryService.deliverWithRetry(webUserId, delivered)

        // Send status update to clear typing indicator
        event.fromUserId?.let { fromUserId ->
            chatService.sendStatusToUser(webUserId, StatusMessage(fromUserId = fromUserId))
        }
    }

    @EventListener(condition = "#event.status.name() == 'PERSISTED'")
    fun onMessagePersisted(event: MessageEvent) {
        // If the PERSISTED event has a title (e.g. LLM just generated one),
        // push a session event so the frontend dropdown updates immediately.
        if (event.title != null && event.toUserId != null) {
            val guideUser = guideUserRepository.findById(event.toUserId!!).orElse(null)
            val webUserId = guideUser?.webUser?.id
            if (webUserId != null) {
                chatService.sendSessionToUser(webUserId, SessionEvent(
                    sessionId = event.conversationId,
                    title = event.title,
                ))
            }
        }

        val narration = narrationCache.consumeForPersistence(event.conversationId)
        if (narration == null) {
            logger.debug("No narration to persist for session {}", event.conversationId)
            return
        }
        try {
            chatSessionRepository.updateMessageNarration(event.conversationId, narration)
            logger.debug("Persisted narration for session {}", event.conversationId)
        } catch (e: Exception) {
            logger.error("Failed to persist narration for session {}: {}", event.conversationId, e.message, e)
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
        // Clean up any cached narration for this conversation
        narrationCache.consumeForPersistence(event.conversationId)
    }
}
