package com.embabel.guide.chat.service

import com.embabel.guide.chat.model.DeliveredMessage
import com.embabel.guide.chat.model.StatusMessage
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

@Service
class ChatService(private val messaging: SimpMessagingTemplate) {

    private val logger = LoggerFactory.getLogger(ChatService::class.java)

    fun sendToUser(toUserId: String, msg: DeliveredMessage) {
        logger.info("[session={}] Sending message to user {} via /queue/messages: {} chars",
            msg.sessionId, toUserId, msg.body.length)
        messaging.convertAndSendToUser(toUserId, "/queue/messages", msg)
        logger.info("[session={}] Message sent to user {}", msg.sessionId, toUserId)
    }

    fun sendStatusToUser(toUserId: String, status: StatusMessage) {
        logger.debug("Sending status to user {} via /queue/status: {}", toUserId, status.status)
        messaging.convertAndSendToUser(toUserId, "/queue/status", status)
    }
}
