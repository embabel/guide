package com.embabel.hub.integrations

import com.embabel.agent.api.models.DeepSeekModels

/**
 * Supported LLM providers for BYOK (Bring Your Own Key).
 * Each provider defines default models for each role in the Guide app.
 */
enum class LlmProvider(
    val chatModel: String,
    val classifierModel: String,
    val narratorModel: String,
    val summarizerModel: String,
    val validationModel: String = chatModel,
) {
    OPENAI(
        chatModel = "gpt-4.1",
        classifierModel = "gpt-4.1-mini",
        narratorModel = "gpt-4.1-mini",
        summarizerModel = "gpt-4.1-nano",
        validationModel = "gpt-4.1-nano",
    ),
    ANTHROPIC(
        chatModel = "claude-sonnet-4-6",
        classifierModel = "claude-haiku-4-5",
        narratorModel = "claude-haiku-4-5",
        summarizerModel = "claude-haiku-4-5",
        validationModel = "claude-haiku-4-5",
    ),
    MISTRAL(
        chatModel = "mistral-large-latest",
        classifierModel = "mistral-small-latest",
        narratorModel = "mistral-small-latest",
        summarizerModel = "mistral-small-latest",
        validationModel = "mistral-small-latest",
    ),
    DEEPSEEK(
        chatModel = DeepSeekModels.DEEPSEEK_V4_PRO,
        classifierModel = DeepSeekModels.DEEPSEEK_V4_FLASH,
        narratorModel = DeepSeekModels.DEEPSEEK_V4_FLASH,
        summarizerModel = DeepSeekModels.DEEPSEEK_V4_FLASH,
    ),

}
