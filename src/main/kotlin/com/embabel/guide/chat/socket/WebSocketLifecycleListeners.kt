package com.embabel.guide.chat.socket

import com.embabel.guide.chat.service.PresenceService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionDisconnectEvent

@Component
class WebSocketLifecycleListeners(private val presence: PresenceService) {
    @EventListener
    fun onDisconnect(ev: SessionDisconnectEvent) {
        presence.removeSession(ev.sessionId)
    }
}
