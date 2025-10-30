package com.embabel.guide.chat.controller

import com.embabel.guide.chat.model.ChatMessage
import com.embabel.guide.chat.model.DeliveredMessage
import com.embabel.guide.chat.service.ChatService
import com.embabel.guide.chat.service.JesseService
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class ChatController(private val chat: ChatService, private val jesseService: JesseService) {

    @MessageMapping("chat.send")
    fun receive(principal: Principal, payload: ChatMessage) {
        val delivered = DeliveredMessage(
            fromUserId = principal.name,
            toUserId = payload.toUserId,
            room = payload.room,
            body = payload.body
        )
        when {
            payload.toUserId == jesseService.jesseUserId -> {
                // Message is being sent to Jesse
                jesseService.receiveMessage(principal.name, payload.body)
            }
            payload.toUserId != null -> chat.sendToUser(payload.toUserId, delivered)
            payload.room != null -> chat.sendToRoom(payload.room, delivered)
            else -> throw IllegalArgumentException("Must include toUserId or room")
        }
    }

    @MessageMapping("chat.sendToJesse")
    fun sendToJesse(principal: Principal, payload: ChatMessage) {
        jesseService.receiveMessage(principal.name, payload.body)
    }
}
