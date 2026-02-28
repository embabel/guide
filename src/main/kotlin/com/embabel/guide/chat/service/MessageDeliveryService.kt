package com.embabel.guide.chat.service

import com.embabel.guide.chat.model.DeliveredMessage
import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

/**
 * Delivers messages via WebSocket with retry and acknowledgment.
 *
 * After sending a message, schedules retries with exponential backoff.
 * Retries are canceled when:
 * - The client acknowledges receipt
 * - The user's presence disappears (they disconnected)
 * - Maximum retry attempts are exhausted
 */
@Service
class MessageDeliveryService(
    private val chatService: ChatService,
    private val presenceService: PresenceService,
    private val taskScheduler: TaskScheduler,
) {
    private val logger = LoggerFactory.getLogger(MessageDeliveryService::class.java)

    private val pendingDeliveries = ConcurrentHashMap<String, PendingDelivery>()

    companion object {
        private const val MAX_ATTEMPTS = 5
        private val BACKOFF_DELAYS_MS = longArrayOf(3_000, 6_000, 12_000, 24_000, 36_000)
    }

    private data class PendingDelivery(
        val toUserId: String,
        val message: DeliveredMessage,
        var attempt: Int = 0,
        var future: ScheduledFuture<*>? = null,
    )

    /**
     * Send a message and schedule retries until acknowledged or abandoned.
     */
    fun deliverWithRetry(toUserId: String, message: DeliveredMessage) {
        chatService.sendToUser(toUserId, message)

        val delivery = PendingDelivery(toUserId = toUserId, message = message)
        pendingDeliveries[message.id] = delivery
        scheduleRetry(delivery)
    }

    /**
     * Client acknowledged receipt — cancel any pending retries.
     */
    fun acknowledge(messageId: String) {
        val removed = pendingDeliveries.remove(messageId)
        if (removed != null) {
            removed.future?.cancel(false)
            logger.debug("Message {} acknowledged by user {}", messageId, removed.toUserId)
        }
    }

    private fun scheduleRetry(delivery: PendingDelivery) {
        val delayMs = BACKOFF_DELAYS_MS.getOrElse(delivery.attempt) { BACKOFF_DELAYS_MS.last() }
        delivery.future = taskScheduler.schedule(
            { retryDelivery(delivery) },
            Instant.now().plusMillis(delayMs),
        )
    }

    private fun retryDelivery(delivery: PendingDelivery) {
        val messageId = delivery.message.id

        // Already acknowledged
        if (!pendingDeliveries.containsKey(messageId)) return

        delivery.attempt++

        if (delivery.attempt >= MAX_ATTEMPTS) {
            pendingDeliveries.remove(messageId)
            logger.info("Giving up delivery of message {} to user {} after {} attempts",
                messageId, delivery.toUserId, delivery.attempt)
            return
        }

        if (!presenceService.isUserPresent(delivery.toUserId)) {
            pendingDeliveries.remove(messageId)
            logger.info("User {} no longer present, abandoning delivery of message {}",
                delivery.toUserId, messageId)
            return
        }

        logger.debug("Retrying delivery of message {} to user {} (attempt {})",
            messageId, delivery.toUserId, delivery.attempt)
        chatService.sendToUser(delivery.toUserId, delivery.message)
        scheduleRetry(delivery)
    }
}