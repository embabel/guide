package com.embabel.guide.chat.security

import com.embabel.guide.domain.GuideUserService
import com.embabel.hub.JwtTokenService
import org.springframework.http.server.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.support.DefaultHandshakeHandler
import java.security.Principal

@Component
class AnonymousPrincipalHandshakeHandler(
    private val guideUserService: GuideUserService,
    private val jwtTokenService: JwtTokenService
) : DefaultHandshakeHandler() {

    override fun determineUser(
        request: ServerHttpRequest,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Principal {
        logger.info("WebSocket handshake - URI: ${request.uri}")

        // First check if there's already a principal from Spring Security
        val existing = request.principal
        if (existing != null) {
            logger.info("Using existing principal from Spring Security: ${existing.name}")
            return existing
        }

        // Try to extract JWT token from query parameter (common for WebSocket)
        val token = extractTokenFromRequest(request)
        if (token != null) {
            logger.info("JWT token found in WebSocket handshake request")
            try {
                val userId = jwtTokenService.validateRefreshToken(token)
                logger.info("JWT token validated successfully for user: $userId")
                return object : Principal {
                    override fun getName(): String = userId
                }
            } catch (e: Exception) {
                logger.warn("JWT token validation failed during WebSocket handshake: ${e.message}", e)
            }
        } else {
            logger.info("No JWT token found in WebSocket handshake request")
        }

        // Fall back to anonymous user
        logger.info("Falling back to anonymous user")
        return object : Principal {
            private val user = guideUserService.findOrCreateAnonymousWebUser()
            private val webUser = requireNotNull(user.webUser) { "webUser should not be null here" }

            override fun getName(): String = webUser.id
        }
    }

    /**
     * Extracts JWT token from query parameters or headers.
     * For WebSocket connections, clients typically send the token as a query parameter
     * since custom headers are not well supported in browser WebSocket APIs.
     */
    private fun extractTokenFromRequest(request: ServerHttpRequest): String? {
        // Check query parameter first (e.g., /ws?token=xxx)
        val uri = request.uri
        val query = uri.query
        if (query != null) {
            val params = query.split("&")
            for (param in params) {
                val parts = param.split("=")
                if (parts.size == 2 && parts[0] == "token") {
                    return parts[1]
                }
            }
        }

        // Check Authorization header as fallback
        val authHeader = request.headers.getFirst("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7)
        }

        return null
    }
}
