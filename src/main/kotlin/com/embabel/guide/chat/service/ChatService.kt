package com.embabel.guide.chat.service

import com.embabel.guide.chat.model.DeliveredMessage
import com.embabel.guide.chat.model.StatusMessage
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

@Service
class ChatService(private val messaging: SimpMessagingTemplate) {

    fun sendToUser(toUserId: String, msg: DeliveredMessage) {
        messaging.convertAndSendToUser(toUserId, "/queue/messages", msg)
    }

    fun sendToRoom(room: String, msg: DeliveredMessage) {
        messaging.convertAndSend("/topic/rooms/$room", msg)
    }

    fun sendStatusToUser(toUserId: String, status: StatusMessage) {
        messaging.convertAndSendToUser(toUserId, "/queue/status", status)
    }
}
