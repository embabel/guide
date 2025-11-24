package com.embabel.guide.chat.controller

import com.embabel.guide.chat.model.DeliveredMessage
import com.embabel.guide.chat.service.ChatService
import com.embabel.guide.chat.service.JesseService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/messages")
class ChatApiController(private val chat: ChatService, private val jesseService: JesseService) {
    data class Outbound(val toUserId: String, val body: String, val fromUserId: String)
    data class JesseMessage(val fromUserId: String, val body: String)

    @PostMapping("/user")
    fun sendToUser(@RequestBody req: Outbound) {
        val msg = DeliveredMessage(
            fromUserId = req.fromUserId,
            toUserId = req.toUserId,
            body = req.body
        )
        chat.sendToUser(req.toUserId, msg)
    }

    @PostMapping("/jesse")
    fun sendToJesse(@RequestBody req: JesseMessage) {
        jesseService.receiveMessage(req.fromUserId, req.body)
    }
}
