package com.embabel.hub.oauth

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.URLEncoder

@Service
class GoogleOAuthService(
    @Value("\${guide.oauth.google.client-id:}") private val clientId: String,
    @Value("\${guide.oauth.google.client-secret:}") private val clientSecret: String,
    @Value("\${guide.oauth.google.redirect-uri:}") private val redirectUri: String,
) {

    private val logger = LoggerFactory.getLogger(GoogleOAuthService::class.java)
    private val restClient = RestClient.create()

    val isConfigured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    fun getAuthorizationUrl(state: String): String {
        val params = mapOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to "openid email profile",
            "state" to state,
            "access_type" to "offline",
            "prompt" to "select_account",
        )
        val query = params.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, Charsets.UTF_8)}"
        }
        return "https://accounts.google.com/o/oauth2/v2/auth?$query"
    }

    fun exchangeCodeForUserInfo(code: String): OAuthUserInfo {
        // Exchange authorization code for tokens
        val tokenResponse = restClient.post()
            .uri("https://oauth2.googleapis.com/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                "code=${URLEncoder.encode(code, Charsets.UTF_8)}" +
                    "&client_id=${URLEncoder.encode(clientId, Charsets.UTF_8)}" +
                    "&client_secret=${URLEncoder.encode(clientSecret, Charsets.UTF_8)}" +
                    "&redirect_uri=${URLEncoder.encode(redirectUri, Charsets.UTF_8)}" +
                    "&grant_type=authorization_code"
            )
            .retrieve()
            .body(Map::class.java) ?: throw OAuthException("Failed to exchange code with Google")

        val accessToken = tokenResponse["access_token"] as? String
            ?: throw OAuthException("No access_token in Google response")

        // Fetch user info
        val userInfo = restClient.get()
            .uri("https://www.googleapis.com/oauth2/v3/userinfo")
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(Map::class.java) ?: throw OAuthException("Failed to fetch Google user info")

        logger.info("Google user info: sub={}, email={}", userInfo["sub"], userInfo["email"])

        return OAuthUserInfo(
            provider = "google",
            providerUserId = userInfo["sub"] as? String ?: throw OAuthException("Missing sub in Google response"),
            email = userInfo["email"] as? String,
            emailVerified = userInfo["email_verified"] as? Boolean ?: false,
            displayName = userInfo["name"] as? String,
            avatarUrl = userInfo["picture"] as? String,
        )
    }
}
