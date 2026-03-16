package com.embabel.hub.integrations

import com.embabel.guide.domain.GuideUserService
import com.embabel.hub.UnauthorizedException
import com.embabel.hub.WelcomeGreeter
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

data class SetKeyRequest(val provider: LlmProvider, val apiKey: String)

data class SetKeyResponse(val encryptedKey: String)

data class RestoreKeyRequest(val provider: LlmProvider, val encryptedKey: String)

data class SetActiveProviderRequest(val provider: LlmProvider)

@RestController
@RequestMapping("/api/hub/integrations")
class IntegrationsController(
    private val userKeyStore: UserKeyStore,
    private val guideUserService: GuideUserService,
    private val welcomeGreeter: WelcomeGreeter,
    private val keyEncryptionService: KeyEncryptionService,
    private val userModelFactory: UserModelFactory,
) {

    private val logger = LoggerFactory.getLogger(IntegrationsController::class.java)

    /**
     * Store an API key for a provider. Validates the key by making a lightweight LLM call.
     * Returns an encrypted blob for the client to cache in localStorage
     * (the client never stores the plaintext key).
     */
    @PutMapping("/key")
    fun setKey(
        @RequestBody request: SetKeyRequest,
        authentication: Authentication?,
    ): ResponseEntity<*> {
        val webUserId = requireUserId(authentication)

        val validationError = userModelFactory.validateKey(request.provider, request.apiKey)
        if (validationError != null) {
            return ResponseEntity.badRequest().body(mapOf("error" to validationError))
        }

        userKeyStore.setKey(webUserId, request.provider, request.apiKey)
        fireWelcome(webUserId)

        val encryptedKey = keyEncryptionService.encrypt(request.apiKey)
        return ResponseEntity.ok(SetKeyResponse(encryptedKey))
    }

    /**
     * Restore a key from an encrypted blob (sent by the client on login/reconnect).
     * Returns 200 if restored successfully, 410 Gone if the blob can't be decrypted
     * (e.g. server restarted with a different key — client should discard the blob).
     */
    @PutMapping("/key/restore")
    fun restoreKey(
        @RequestBody request: RestoreKeyRequest,
        authentication: Authentication?,
    ): ResponseEntity<Void> {
        val webUserId = requireUserId(authentication)
        val apiKey = keyEncryptionService.decrypt(request.encryptedKey)
            ?: return ResponseEntity.status(410).build()
        userKeyStore.setKey(webUserId, request.provider, apiKey)

        // No welcome on restore — the user has been here before
        return ResponseEntity.ok().build()
    }

    @PutMapping("/active-provider")
    fun setActiveProvider(
        @RequestBody request: SetActiveProviderRequest,
        authentication: Authentication?,
    ): ResponseEntity<Void> {
        val userId = requireUserId(authentication)
        userKeyStore.setActiveProvider(userId, request.provider)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/key/{provider}")
    fun removeKey(
        @PathVariable provider: LlmProvider,
        authentication: Authentication?,
    ): ResponseEntity<Void> {
        val userId = requireUserId(authentication)
        userKeyStore.removeKey(userId, provider)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/keys")
    fun getKeys(authentication: Authentication?): List<ProviderStatus> {
        val userId = requireUserId(authentication)
        return userKeyStore.getProviders(userId)
    }

    private fun fireWelcome(webUserId: String) {
        try {
            val guideUser = guideUserService.findByWebUserId(webUserId).orElse(null) ?: return
            if (guideUser.core.welcomed) return

            val displayName = guideUser.webUser?.displayName ?: "there"
            logger.info("First BYOK key set for user {} — firing welcome greeting", webUserId)

            guideUser.core.welcomed = true
            guideUserService.saveUser(guideUser)

            welcomeGreeter.greetNewUser(
                guideUserId = guideUser.core.id,
                webUserId = webUserId,
                displayName = displayName,
            )
        } catch (e: Exception) {
            logger.error("Failed to fire welcome greeting for user {}: {}", webUserId, e.message, e)
        }
    }

    private fun requireUserId(authentication: Authentication?): String =
        authentication?.principal as? String
            ?: throw UnauthorizedException()
}