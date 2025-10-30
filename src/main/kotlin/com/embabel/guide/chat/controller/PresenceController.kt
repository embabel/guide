package com.embabel.guide.chat.controller

import com.embabel.guide.chat.model.PresencePing
import com.embabel.guide.chat.service.PresenceService
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class PresenceController(private val presence: PresenceService) {

    @MessageMapping("presence.ping")
    fun ping(
        principal: Principal,
        payload: PresencePing,
        @Header("simpSessionId") sessionId: String
    ) {
        presence.touch(userId = principal.name, sessionId = sessionId, status = payload.status)
    }
}
