package com.embabel.guide.chat.security

import com.embabel.guide.domain.GuideUserService
import org.springframework.http.server.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.support.DefaultHandshakeHandler
import java.security.Principal
import java.util.UUID

@Component
class AnonymousPrincipalHandshakeHandler(
    private val guideUserService: GuideUserService
) : DefaultHandshakeHandler() {

    override fun determineUser(
        request: ServerHttpRequest,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Principal {
        val existing = request.principal
        return existing ?: object : Principal {
            private val user = guideUserService.findOrCreateAnonymousWebUser()
            override fun getName(): String = user.id
        }
    }
}
