package com.embabel.hub.integrations

import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.api.common.PromptRunner
import com.embabel.agent.spi.LlmService
import com.embabel.agent.spi.support.springai.SpringAiLlmService
import com.embabel.guide.GuideProperties
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service

/**
 * Registers the fake [SetupRequiredChatModel] as an [LlmService] bean so that
 * [com.embabel.common.ai.model.ConfigurableModelProvider] can find it as the default-llm
 * when no real provider starters are present.
 */
@Configuration(proxyBeanMethods = false)
class SetupRequiredLlmConfig {
    @Primary
    @Bean(SetupRequiredChatModel.MODEL_NAME)
    fun setupRequiredLlmService(): LlmService<*> = SpringAiLlmService(
        name = SetupRequiredChatModel.MODEL_NAME,
        provider = "none",
        chatModel = SetupRequiredChatModel(),
    )
}

/**
 * Resolves LLM access for a user: BYOK key if configured, otherwise server default.
 * Inject this anywhere per-user LLM resolution is needed.
 *
 * If no server-side provider is configured (cloud/hub deployment), the fallback
 * returns a [SetupRequiredChatModel] that tells users to add their own key.
 */
@Service
class UserLlmResolver(
    guideProperties: GuideProperties,
    private val userKeyStore: UserKeyStore,
    private val userModelFactory: UserModelFactory,
    private val setupRequiredService: LlmService<*>,
) {

    private val logger = LoggerFactory.getLogger(UserLlmResolver::class.java)

    /**
     * The server-wide default provider, or null for pure BYOK deployments.
     * Priority: explicit config > auto-detect from env vars > null (no keys).
     */
    val serverProvider: LlmProvider? = guideProperties.defaultProvider ?: detectProvider()

    init {
        if (serverProvider != null) {
            logger.info("Server default LLM provider: {} (explicit={})", serverProvider, guideProperties.defaultProvider != null)
        } else {
            logger.info("No server LLM provider configured — pure BYOK mode")
        }
    }

    /**
     * Returns a [PromptRunner] configured for the given user and role.
     * Resolution order:
     * 1. User's BYOK key (if configured)
     * 2. Server default provider (if available)
     * 3. Setup-required fake LLM (tells user to add a key)
     */
    /**
     * Returns true if the user has a real LLM available (either BYOK or server default).
     */
    fun hasLlm(userId: String): Boolean =
        userKeyStore.getActiveKey(userId) != null || serverProvider != null

    fun resolve(ctx: OperationContext, userId: String, role: LlmRole): PromptRunner {
        val activeKey = userKeyStore.getActiveKey(userId)
        if (activeKey != null) {
            val (provider, apiKey) = activeKey
            val model = role.modelSelector(provider)
            val llmService = userModelFactory.getLlmService(provider, model, apiKey)
            return ctx.ai().withLlmService(llmService)
        }
        val provider = serverProvider
        if (provider != null) {
            val model = role.modelSelector(provider)
            return ctx.ai().withLlm(model)
        }
        return ctx.ai().withLlmService(setupRequiredService)
    }

    companion object {

        private val PROVIDER_ENV_KEYS = listOf(
            LlmProvider.OPENAI to "OPENAI_API_KEY",
            LlmProvider.ANTHROPIC to "ANTHROPIC_API_KEY",
            LlmProvider.MISTRAL to "MISTRAL_API_KEY",
            LlmProvider.DEEPSEEK to "DEEPSEEK_API_KEY",
        )

        /**
         * Auto-detect which provider to use based on which API key env vars are set.
         * Returns the first provider whose key is present, or null if none found.
         */
        fun detectProvider(): LlmProvider? {
            for ((provider, envKey) in PROVIDER_ENV_KEYS) {
                if (!System.getenv(envKey).isNullOrBlank()) {
                    return provider
                }
            }
            return null
        }
    }
}