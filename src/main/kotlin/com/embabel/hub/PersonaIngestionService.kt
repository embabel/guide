package com.embabel.hub

import com.embabel.agent.spi.support.springai.SpringAiLlmService
import com.embabel.common.textio.template.TemplateRenderer
import com.embabel.guide.domain.PersonaData
import com.embabel.hub.integrations.LlmProvider
import com.embabel.hub.integrations.UserKeyStore
import com.embabel.hub.integrations.UserModelFactory
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Validates and ingests a user-defined persona prompt.
 *
 * Validation chain:
 * 1. Length cap (≤ MAX_PROMPT_LENGTH chars)
 * 2. Pattern blocklist (obvious injection keywords)
 * 3. LLM safety check + description generation (uses user's BYOK validationModel)
 *
 * On success, returns a ready-to-save [PersonaData] with a generated description.
 * On failure, returns an error message the caller can surface to the user.
 */
@Service
class PersonaIngestionService(
    private val userKeyStore: UserKeyStore,
    private val userModelFactory: UserModelFactory,
    private val templateRenderer: TemplateRenderer,
) {

    private val logger = LoggerFactory.getLogger(PersonaIngestionService::class.java)
    private val objectMapper = jacksonObjectMapper()

    // Loaded once at first use; the template has no per-request variables beyond max_length
    private val classifierSystemPrompt: String by lazy {
        templateRenderer.renderLoadedTemplate(
            "ingestion/persona_classifier",
            mapOf("max_length" to MAX_PROMPT_LENGTH),
        )
    }

    sealed interface IngestionResult {
        data class Success(val personaData: PersonaData) : IngestionResult
        data class Failure(val reason: String) : IngestionResult
    }

    fun ingest(userId: String, name: String, rawPrompt: String, voice: String?, effects: List<AudioEffect>?): IngestionResult {
        val trimmed = rawPrompt.trim()

        val lengthError = checkLength(trimmed)
        if (lengthError != null) return IngestionResult.Failure(lengthError)

        val blocklistError = checkBlocklist(trimmed)
        if (blocklistError != null) return IngestionResult.Failure(blocklistError)

        val activeKey = userKeyStore.getActiveKey(userId)
            ?: return IngestionResult.Failure("You need an API key configured to create custom personas.")

        val (provider, apiKey) = activeKey
        return try {
            val llmResult = classifyAndDescribe(trimmed, provider, apiKey)
            when {
                !llmResult.safe -> IngestionResult.Failure(
                    llmResult.reason ?: "This prompt was flagged as unsafe."
                )
                else -> IngestionResult.Success(
                    PersonaData(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        prompt = FRAME_PREFIX + trimmed + FRAME_SUFFIX,
                        description = llmResult.description,
                        voice = voice,
                        effects = effects,
                        isSystem = false,
                    )
                )
            }
        } catch (e: Exception) {
            logger.warn("LLM persona validation failed for user={}: {}", userId, e.message)
            IngestionResult.Failure("Could not validate persona: ${e.message}")
        }
    }

    private fun checkLength(prompt: String): String? =
        if (prompt.length > MAX_PROMPT_LENGTH)
            "Persona prompt must be $MAX_PROMPT_LENGTH characters or fewer (yours is ${prompt.length})."
        else null

    private fun checkBlocklist(prompt: String): String? {
        val lower = prompt.lowercase()
        val hit = BLOCKLIST.firstOrNull { lower.contains(it) }
        return if (hit != null) "Prompt contains disallowed content." else null
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class ClassifyResponse(
        val safe: Boolean = false,
        val description: String? = null,
        val reason: String? = null,
    )

    private fun classifyAndDescribe(prompt: String, provider: LlmProvider, apiKey: String): ClassifyResponse {
        val model = provider.validationModel
        val llmService = userModelFactory.getLlmService(provider, model, apiKey) as SpringAiLlmService
        val chatModel = llmService.chatModel

        val response = chatModel.call(
            Prompt(
                listOf(
                    SystemMessage(classifierSystemPrompt),
                    UserMessage(prompt),
                )
            )
        )
        val text = response.result.output.text ?: return ClassifyResponse(safe = false, reason = "Empty response from LLM.")
        return try {
            // Strip markdown code fences if present
            val json = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            objectMapper.readValue(json, ClassifyResponse::class.java)
        } catch (e: Exception) {
            logger.debug("Failed to parse LLM classify response: {}", text)
            ClassifyResponse(safe = false, reason = "Could not parse safety check response.")
        }
    }

    companion object {
        const val MAX_PROMPT_LENGTH = 500

        private const val FRAME_PREFIX = "[PERSONA — style and tone only]\n"
        private const val FRAME_SUFFIX = "\n[END PERSONA]"

        private val BLOCKLIST = listOf(
            "ignore previous",
            "ignore all previous",
            "disregard previous",
            "you are now",
            "new instructions",
            "bypass",
            "jailbreak",
            "act as if",
            "pretend you are",
            "your new role",
            "override",
        )

    }
}
