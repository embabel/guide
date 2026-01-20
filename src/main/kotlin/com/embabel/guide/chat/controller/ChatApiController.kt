package com.embabel.guide.chat.controller

import com.embabel.guide.chat.service.JesseService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/messages")
class ChatApiController(private val jesseService: JesseService) {

    data class SendMessageRequest(val sessionId: String, val fromWebUserId: String, val body: String)

    @PostMapping("/send")
    fun sendMessage(@RequestBody req: SendMessageRequest) {
        jesseService.receiveMessage(
            sessionId = req.sessionId,
            fromWebUserId = req.fromWebUserId,
            message = req.body
        )
    }
}
