package com.embabel.guide.chat.service

import com.embabel.guide.chat.model.DeliveredMessage
import com.embabel.guide.chat.model.StatusMessage
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
    private val ragAdapter: RagServiceAdapter
) {
    val jesseUserId: String
        get() = JESSE_USER_ID

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

    fun sendMessageToUser(toUserId: String, message: String) {
        logger.debug("Jesse sending message to user: {}", toUserId)
        val deliveredMessage = DeliveredMessage(
            fromUserId = JESSE_USER_ID,
            toUserId = toUserId,
            body = message
        )
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

    fun receiveMessage(fromUserId: String, message: String) {
        logger.info("Jesse received message from user: {}", fromUserId)

        coroutineScope.launch {
            try {
                // Send status updates to the user while processing
                val response = ragAdapter.sendMessage(message, fromUserId) { event ->
                    logger.debug("RAG event for user {}: {}", fromUserId, event)
                    sendStatusToUser(fromUserId, event)
                }

                // Send the final response
                sendMessageToUser(fromUserId, response)
            } catch (e: Exception) {
                logger.error("Error processing message from user {}: {}", fromUserId, e.message, e)
                sendStatusToUser(fromUserId, "Error processing your request")
                sendMessageToUser(fromUserId, "Sorry, I encountered an error while processing your message. Please try again!")
            }
        }
    }

}
