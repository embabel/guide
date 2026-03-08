package com.embabel.hub.integrations

import com.embabel.agent.spi.LlmService
import com.embabel.agent.spi.support.springai.SpringAiLlmService
import org.slf4j.LoggerFactory
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.anthropic.api.AnthropicApi
import org.springframework.ai.deepseek.DeepSeekChatModel
import org.springframework.ai.deepseek.DeepSeekChatOptions
import org.springframework.ai.deepseek.api.DeepSeekApi
import org.springframework.ai.mistralai.MistralAiChatModel
import org.springframework.ai.mistralai.MistralAiChatOptions
import org.springframework.ai.mistralai.api.MistralAiApi
import org.springframework.ai.model.tool.ToolCallingManager
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
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
            val model = provider.validationModel
            val service = createLlmService(provider, model, apiKey)
            val chatModel = (service as SpringAiLlmService).chatModel
            chatModel.call("Hi")
            null
        } catch (e: Exception) {
            val message = e.cause?.message ?: e.message ?: "Unknown error"
            logger.warn("API key validation failed for {}: {}", provider, message)
            when {
                message.contains("401") || message.contains("unauthorized", ignoreCase = true) ||
                    message.contains("invalid", ignoreCase = true) && message.contains("key", ignoreCase = true) ->
                    "Invalid API key"
                message.contains("403") || message.contains("forbidden", ignoreCase = true) ->
                    "API key lacks required permissions"
                else -> "Could not validate key: $message"
            }
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
            LlmProvider.OPENAI -> createOpenAiService(model, apiKey)
            LlmProvider.ANTHROPIC -> createAnthropicService(model, apiKey)
            LlmProvider.MISTRAL -> createMistralService(model, apiKey)
            LlmProvider.DEEPSEEK -> createDeepSeekService(model, apiKey)
        }
    }

    private fun createOpenAiService(model: String, apiKey: String): SpringAiLlmService {
        val api = OpenAiApi.Builder()
            .apiKey(apiKey)
            .build()
        val chatModel = OpenAiChatModel.builder()
            .openAiApi(api)
            .toolCallingManager(ToolCallingManager.builder().build())
            .defaultOptions(
                OpenAiChatOptions.builder()
                    .model(model)
                    .build()
            )
            .build()
        return SpringAiLlmService(
            name = model,
            provider = "openai",
            chatModel = chatModel,
        )
    }

    private fun createAnthropicService(model: String, apiKey: String): SpringAiLlmService {
        val api = AnthropicApi.builder()
            .apiKey(apiKey)
            .build()
        val chatModel = AnthropicChatModel.builder()
            .anthropicApi(api)
            .toolCallingManager(ToolCallingManager.builder().build())
            .defaultOptions(
                AnthropicChatOptions.builder()
                    .model(model)
                    .maxTokens(4096)
                    .build()
            )
            .build()
        return SpringAiLlmService(
            name = model,
            provider = "anthropic",
            chatModel = chatModel,
        )
    }

    private fun createMistralService(model: String, apiKey: String): SpringAiLlmService {
        val api = MistralAiApi.builder()
            .apiKey(apiKey)
            .build()
        val chatModel = MistralAiChatModel.builder()
            .mistralAiApi(api)
            .toolCallingManager(ToolCallingManager.builder().build())
            .defaultOptions(
                MistralAiChatOptions.builder()
                    .model(model)
                    .build()
            )
            .build()
        return SpringAiLlmService(
            name = model,
            provider = "mistralai",
            chatModel = chatModel,
        )
    }

    private fun createDeepSeekService(model: String, apiKey: String): SpringAiLlmService {
        val api = DeepSeekApi.builder()
            .apiKey(apiKey)
            .build()
        val chatModel = DeepSeekChatModel.builder()
            .deepSeekApi(api)
            .toolCallingManager(ToolCallingManager.builder().build())
            .defaultOptions(
                DeepSeekChatOptions.builder()
                    .model(model)
                    .build()
            )
            .build()
        return SpringAiLlmService(
            name = model,
            provider = "deepseek",
            chatModel = chatModel,
        )
    }

}
