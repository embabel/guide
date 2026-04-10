package com.embabel.hub.oauth

import com.embabel.chat.store.util.UUIDv7
import com.embabel.guide.domain.GuideUserService
import com.embabel.guide.domain.OAuthProviderData
import com.embabel.guide.domain.WebUserData
import com.embabel.hub.JwtTokenService
import com.embabel.hub.LoginResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class OAuthLoginService(
    private val guideUserService: GuideUserService,
    private val jwtTokenService: JwtTokenService,
    private val googleOAuthService: GoogleOAuthService,
    private val gitHubOAuthService: GitHubOAuthService,
) {

    private val logger = LoggerFactory.getLogger(OAuthLoginService::class.java)

    fun handleOAuthCallback(provider: String, code: String): LoginResponse {
        val userInfo = when (provider) {
            "google" -> googleOAuthService.exchangeCodeForUserInfo(code)
            "github" -> gitHubOAuthService.exchangeCodeForUserInfo(code)
            else -> throw OAuthException("Unsupported OAuth provider: $provider")
        }

        // 1. Check if user already linked via this OAuth provider
        val existingByProvider = guideUserService.findByOAuthProvider(userInfo.provider, userInfo.providerUserId)
        if (existingByProvider.isPresent) {
            val guideUser = existingByProvider.get()
            val webUser = guideUser.webUser!!
            logger.info("OAuth login: existing user {} via {}", webUser.id, provider)
            return buildLoginResponse(webUser.id, webUser, guideUser.persona.id)
        }

        // 2. Check if user exists with matching email (auto-link)
        if (userInfo.email != null && userInfo.emailVerified) {
            val existingByEmail = guideUserService.findByWebUserEmail(userInfo.email)
            if (existingByEmail.isPresent) {
                val guideUser = existingByEmail.get()
                val webUser = guideUser.webUser!!

                // Link OAuth provider to existing user
                val oauthData = buildOAuthProviderData(userInfo)
                val updated = guideUser.copy(
                    oauthProviders = guideUser.oauthProviders + oauthData,
                )
                // Also mark email as verified since the OAuth provider confirmed it
                webUser.emailVerified = true
                guideUserService.saveUser(updated)

                logger.info("OAuth login: linked {} to existing user {} by email", provider, webUser.id)
                return buildLoginResponse(webUser.id, webUser, guideUser.persona.id)
            }
        }

        // 3. Create new user
        val userId = UUIDv7.generateString()
        val username = generateUsername(userInfo.email, userInfo.displayName)
        val webUser = WebUserData(
            id = userId,
            displayName = userInfo.displayName ?: username,
            userName = username,
            userEmail = userInfo.email,
            passwordHash = null,
            refreshToken = null,
            emailVerified = userInfo.emailVerified,
        )
        val savedUser = guideUserService.saveFromWebUser(webUser)

        // Link OAuth provider
        val oauthData = buildOAuthProviderData(userInfo)
        val updated = savedUser.copy(
            oauthProviders = savedUser.oauthProviders + oauthData,
        )
        guideUserService.saveUser(updated)

        logger.info("OAuth login: created new user {} via {}", userId, provider)
        return buildLoginResponse(userId, webUser, savedUser.persona.id)
    }

    private fun buildOAuthProviderData(userInfo: OAuthUserInfo) = OAuthProviderData(
        id = "${userInfo.provider}_${userInfo.providerUserId}",
        provider = userInfo.provider,
        providerUserId = userInfo.providerUserId,
        email = userInfo.email,
        displayName = userInfo.displayName,
        avatarUrl = userInfo.avatarUrl,
    )

    private fun buildLoginResponse(webUserId: String, webUser: WebUserData, personaId: String): LoginResponse {
        val token = jwtTokenService.generateRefreshToken(webUserId)
        val expiresAt = Instant.now().plusSeconds(jwtTokenService.tokenExpirationSeconds)
        return LoginResponse(
            token = token,
            expiresAt = expiresAt,
            userId = webUserId,
            username = webUser.userName,
            displayName = webUser.displayName,
            email = webUser.userEmail ?: "",
            persona = personaId,
            emailVerified = webUser.emailVerified,
        )
    }

    private fun generateUsername(email: String?, displayName: String?): String {
        val base = when {
            email != null -> email.substringBefore('@').lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .take(20)
            displayName != null -> displayName.lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .take(20)
            else -> "user"
        }

        // Check uniqueness, append random suffix if taken
        if (guideUserService.findByWebUserName(base).isEmpty) return base

        repeat(10) {
            val candidate = "${base}_${randomSuffix()}"
            if (guideUserService.findByWebUserName(candidate).isEmpty) return candidate
        }

        return "${base}_${System.currentTimeMillis()}"
    }

    private fun randomSuffix(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..4).map { chars.random() }.joinToString("")
    }
}
