package com.embabel.hub.integrations

import com.embabel.agent.api.models.DeepSeekModels
import com.embabel.agent.api.models.MistralAiModels
import com.embabel.agent.api.models.OpenAiModels
import com.embabel.agent.config.models.anthropic.AnthropicModelFactory
import com.embabel.agent.openai.OpenAiCompatibleModelFactory
import com.embabel.agent.spi.InvalidApiKeyException
import com.embabel.agent.spi.LlmService
import com.embabel.common.ai.model.PricingModel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Creates and caches [LlmService] instances from user-provided API keys.
 * Each unique provider+model+key combination gets its own cached service.
 * Entries unused for longer than [ttl] are evicted on the next access.
 */
@Service
class UserModelFactory(
    private val ttl: Duration = DEFAULT_TTL,
) {

    private val logger = LoggerFactory.getLogger(UserModelFactory::class.java)

    private data class CachedEntry(
        val service: LlmService<*>,
        var lastUsed: Instant = Instant.now(),
    )

    private val cache = ConcurrentHashMap<String, CachedEntry>()

    fun getLlmService(provider: LlmProvider, model: String, apiKey: String): LlmService<*> {
        evictStale()
        val cacheKey = "${provider.name}:$model:${apiKey.hashCode()}"
        val entry = cache.compute(cacheKey) { _, existing ->
            if (existing != null) {
                existing.lastUsed = Instant.now()
                existing
            } else {
                logger.info("Creating new LlmService for provider={}, model={}", provider, model)
                CachedEntry(createLlmService(provider, model, apiKey))
            }
        }!!
        return entry.service
    }

    fun cacheSize(): Int = cache.size

    /**
     * Validates an API key by making a minimal LLM call.
     * Returns null if the key is valid, or an error message if not.
     */
    fun validateKey(provider: LlmProvider, apiKey: String): String? {
        return try {
            when (provider) {
                LlmProvider.OPENAI -> OpenAiCompatibleModelFactory.openAi(apiKey).buildValidated()
                LlmProvider.ANTHROPIC -> AnthropicModelFactory(apiKey = apiKey).buildValidated()
                LlmProvider.MISTRAL -> OpenAiCompatibleModelFactory.mistral(apiKey).buildValidated()
                LlmProvider.DEEPSEEK -> OpenAiCompatibleModelFactory.deepSeek(apiKey).buildValidated()
            }
            null
        } catch (e: InvalidApiKeyException) {
            logger.debug("API key validation failed for {}: {}", provider, e.message)
            "Invalid API key"
        } catch (e: Exception) {
            logger.debug("API key validation failed for {}: {}", provider, e.message)
            "Could not validate key: ${e.message}"
        }
    }

    private fun evictStale() {
        val cutoff = Instant.now().minus(ttl)
        val iter = cache.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.value.lastUsed.isBefore(cutoff)) {
                logger.info("Evicting stale LlmService: {}", entry.key)
                iter.remove()
            }
        }
    }

    companion object {
        val DEFAULT_TTL: Duration = Duration.ofMinutes(5)
    }

    private fun createLlmService(provider: LlmProvider, model: String, apiKey: String): LlmService<*> {
        return when (provider) {
            LlmProvider.OPENAI -> OpenAiCompatibleModelFactory(null, apiKey, null, null)
                .openAiCompatibleLlm(model, PricingModel.ALL_YOU_CAN_EAT, OpenAiModels.PROVIDER, null)
            LlmProvider.ANTHROPIC -> AnthropicModelFactory(apiKey = apiKey).build(model)
            LlmProvider.MISTRAL -> OpenAiCompatibleModelFactory("https://api.mistral.ai/v1", apiKey, null, null)
                .openAiCompatibleLlm(model, PricingModel.ALL_YOU_CAN_EAT, MistralAiModels.PROVIDER, null)
            LlmProvider.DEEPSEEK -> OpenAiCompatibleModelFactory("https://api.deepseek.com", apiKey, null, null)
                .openAiCompatibleLlm(model, PricingModel.ALL_YOU_CAN_EAT, DeepSeekModels.PROVIDER, null)
        }
    }

}
