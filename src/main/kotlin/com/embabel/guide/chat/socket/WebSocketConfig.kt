package com.embabel.guide.chat.socket

import com.embabel.guide.chat.security.AnonymousPrincipalHandshakeHandler
import com.embabel.guide.chat.security.RequirePrincipalInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val handshakeHandler: AnonymousPrincipalHandshakeHandler,
    private val requirePrincipalInterceptor: RequirePrincipalInterceptor,
) : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .setHandshakeHandler(handshakeHandler)
            .withSockJS()
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic", "/queue")
        registry.setApplicationDestinationPrefixes("/app")
        registry.setUserDestinationPrefix("/user")
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(requirePrincipalInterceptor)
    }
}
