package com.embabel.hub.integrations

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

data class UserKeyConfig(
    val keys: Map<LlmProvider, String> = emptyMap(),
    val activeProvider: LlmProvider? = null,
)

data class ProviderStatus(
    val provider: LlmProvider,
    val configured: Boolean,
    val active: Boolean,
    val keyHint: String? = null,
)

/**
 * In-memory store for per-user LLM provider API keys.
 * Keys are not persisted — they exist only for the lifetime of the server process.
 */
@Service
class UserKeyStore {

    private val configs = ConcurrentHashMap<String, UserKeyConfig>()

    fun setKey(userId: String, provider: LlmProvider, apiKey: String) {
        configs.compute(userId) { _, existing ->
            val current = existing ?: UserKeyConfig()
            current.copy(
                keys = current.keys + (provider to apiKey),
                activeProvider = current.activeProvider ?: provider,
            )
        }
    }

    fun setActiveProvider(userId: String, provider: LlmProvider) {
        configs.computeIfPresent(userId) { _, current ->
            require(current.keys.containsKey(provider)) { "No key configured for provider: $provider" }
            current.copy(activeProvider = provider)
        }
    }

    fun getConfig(userId: String): UserKeyConfig? = configs[userId]

    /**
     * Returns the active provider and API key for the user, or null if none configured.
     */
    fun getActiveKey(userId: String): Pair<LlmProvider, String>? {
        val config = configs[userId] ?: return null
        val provider = config.activeProvider ?: return null
        val apiKey = config.keys[provider] ?: return null
        return provider to apiKey
    }

    fun removeKey(userId: String, provider: LlmProvider) {
        configs.computeIfPresent(userId) { _, current ->
            val newKeys = current.keys - provider
            if (newKeys.isEmpty()) {
                null // Remove entry entirely
            } else {
                current.copy(
                    keys = newKeys,
                    activeProvider = if (current.activeProvider == provider) newKeys.keys.first() else current.activeProvider,
                )
            }
        }
    }

    fun getProviders(userId: String): List<ProviderStatus> {
        val config = configs[userId]
        return LlmProvider.entries.map { provider ->
            val key = config?.keys?.get(provider)
            ProviderStatus(
                provider = provider,
                configured = key != null,
                active = config?.activeProvider == provider,
                keyHint = key?.maskKey(),
            )
        }
    }

    companion object {
        private const val VISIBLE_SUFFIX_LENGTH = 4
        private const val MASK_LENGTH = 32

        /**
         * Masks an API key with a fixed-length prefix, showing only the last few characters.
         * E.g. "sk-abc123xyz" → "••••••••c123xyz"
         */
        fun String.maskKey(): String {
            if (length <= VISIBLE_SUFFIX_LENGTH) return "•".repeat(MASK_LENGTH)
            return "•".repeat(MASK_LENGTH) + substring(length - VISIBLE_SUFFIX_LENGTH)
        }
    }
}