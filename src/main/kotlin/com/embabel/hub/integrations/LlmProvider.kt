package com.embabel.hub.integrations

import com.embabel.agent.api.models.AnthropicModels
import com.embabel.agent.api.models.DeepSeekModels
import com.embabel.agent.api.models.OpenAiModels

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
        chatModel = OpenAiModels.GPT_41,
        classifierModel = OpenAiModels.GPT_41_MINI,
        narratorModel = OpenAiModels.GPT_41_MINI,
        summarizerModel = OpenAiModels.GPT_41_NANO,
        validationModel = OpenAiModels.GPT_41_NANO,
    ),
    ANTHROPIC(
        chatModel = AnthropicModels.CLAUDE_SONNET_4_6,
        classifierModel = AnthropicModels.CLAUDE_HAIKU_4_5,
        narratorModel = AnthropicModels.CLAUDE_HAIKU_4_5,
        summarizerModel = AnthropicModels.CLAUDE_HAIKU_4_5,
        validationModel = AnthropicModels.CLAUDE_HAIKU_4_5,
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
