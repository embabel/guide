package com.embabel.guide.chat.security

import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.stereotype.Component

/**
 * Drops inbound STOMP SEND frames that arrive without a principal on the
 * WebSocket session. The handshake handler (AnonymousPrincipalHandshakeHandler)
 * is supposed to attach one — JWT-derived or anonymous-fallback. If it didn't,
 * the connection is in a half-state (e.g. SockJS landed without going through
 * the handshake) and dispatching to @MessageMapping handlers would NPE.
 */
@Component
class RequirePrincipalInterceptor : ChannelInterceptor {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = StompHeaderAccessor.wrap(message)
        if (accessor.command == StompCommand.SEND && accessor.user == null) {
            logger.warn(
                "Dropping SEND to {}: no principal on WebSocket session (sessionId={})",
                accessor.destination, accessor.sessionId
            )
            return null
        }
        return message
    }
}