package com.embabel.hub.oauth

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.URLEncoder

@Service
class GitHubOAuthService(
    @Value("\${guide.oauth.github.client-id:}") private val clientId: String,
    @Value("\${guide.oauth.github.client-secret:}") private val clientSecret: String,
    @Value("\${guide.oauth.github.redirect-uri:}") private val redirectUri: String,
) {

    private val logger = LoggerFactory.getLogger(GitHubOAuthService::class.java)
    private val restClient = RestClient.create()

    val isConfigured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    fun getAuthorizationUrl(state: String): String {
        val params = mapOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "scope" to "user:email",
            "state" to state,
        )
        val query = params.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, Charsets.UTF_8)}"
        }
        return "https://github.com/login/oauth/authorize?$query"
    }

    @Suppress("UNCHECKED_CAST")
    fun exchangeCodeForUserInfo(code: String): OAuthUserInfo {
        // Exchange authorization code for access token
        val tokenResponse = restClient.post()
            .uri("https://github.com/login/oauth/access_token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .body(
                "code=${URLEncoder.encode(code, Charsets.UTF_8)}" +
                    "&client_id=${URLEncoder.encode(clientId, Charsets.UTF_8)}" +
                    "&client_secret=${URLEncoder.encode(clientSecret, Charsets.UTF_8)}" +
                    "&redirect_uri=${URLEncoder.encode(redirectUri, Charsets.UTF_8)}"
            )
            .retrieve()
            .body(Map::class.java) ?: throw OAuthException("Failed to exchange code with GitHub")

        val accessToken = tokenResponse["access_token"] as? String
            ?: throw OAuthException("No access_token in GitHub response: ${tokenResponse["error_description"] ?: tokenResponse["error"]}")

        // Fetch user profile
        val profile = restClient.get()
            .uri("https://api.github.com/user")
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/vnd.github+json")
            .retrieve()
            .body(Map::class.java) ?: throw OAuthException("Failed to fetch GitHub user profile")

        // Fetch verified primary email
        val emails = restClient.get()
            .uri("https://api.github.com/user/emails")
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/vnd.github+json")
            .retrieve()
            .body(List::class.java) as? List<Map<String, Any?>> ?: emptyList()

        val primaryEmail = emails.firstOrNull { it["primary"] == true && it["verified"] == true }
        val email = primaryEmail?.get("email") as? String
        val emailVerified = primaryEmail?.get("verified") as? Boolean ?: false

        val userId = profile["id"]?.toString()
            ?: throw OAuthException("Missing id in GitHub response")

        logger.info("GitHub user info: id={}, login={}, email={}", userId, profile["login"], email)

        return OAuthUserInfo(
            provider = "github",
            providerUserId = userId,
            email = email,
            emailVerified = emailVerified,
            displayName = profile["name"] as? String ?: profile["login"] as? String,
            avatarUrl = profile["avatar_url"] as? String,
        )
    }
}
