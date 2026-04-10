package com.embabel.hub.oauth

import com.embabel.hub.LoginResponse
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("/api/hub/oauth")
class OAuthController(
    private val oAuthLoginService: OAuthLoginService,
    private val googleOAuthService: GoogleOAuthService,
    private val gitHubOAuthService: GitHubOAuthService,
) {

    private val logger = LoggerFactory.getLogger(OAuthController::class.java)

    // Short-lived state store for CSRF protection (10 minute TTL)
    private val pendingStates = ConcurrentHashMap<String, Long>()
    private val stateTtlMs = 10 * 60 * 1000L

    @GetMapping("/{provider}/authorize")
    fun authorize(@PathVariable provider: String): AuthorizeResponse {
        val state = generateState()
        pendingStates[state] = System.currentTimeMillis()
        cleanExpiredStates()

        val url = when (provider) {
            "google" -> {
                if (!googleOAuthService.isConfigured) throw OAuthException("Google OAuth not configured")
                googleOAuthService.getAuthorizationUrl(state)
            }
            "github" -> {
                if (!gitHubOAuthService.isConfigured) throw OAuthException("GitHub OAuth not configured")
                gitHubOAuthService.getAuthorizationUrl(state)
            }
            else -> throw OAuthException("Unsupported OAuth provider: $provider")
        }

        logger.info("Generated OAuth authorize URL for provider={}", provider)
        return AuthorizeResponse(url, state)
    }

    @PostMapping("/{provider}/callback")
    fun callback(
        @PathVariable provider: String,
        @RequestBody request: OAuthCallbackRequest,
    ): LoginResponse {
        // Validate state
        val stateTimestamp = pendingStates.remove(request.state)
        if (stateTimestamp == null || System.currentTimeMillis() - stateTimestamp > stateTtlMs) {
            throw OAuthException("Invalid or expired OAuth state parameter")
        }

        return oAuthLoginService.handleOAuthCallback(provider, request.code)
    }

    private fun generateState(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun cleanExpiredStates() {
        val now = System.currentTimeMillis()
        pendingStates.entries.removeIf { now - it.value > stateTtlMs }
    }
}

data class AuthorizeResponse(val url: String, val state: String)
data class OAuthCallbackRequest(val code: String, val state: String)
