package com.embabel.hub.integrations

/**
 * Roles that use an LLM in the Guide app.
 * Each role knows how to select the right model from a [LlmProvider].
 */
enum class LlmRole(
    val modelSelector: (LlmProvider) -> String,
) {
    CHAT({ it.chatModel }),
    CLASSIFIER({ it.classifierModel }),
    NARRATOR({ it.narratorModel }),
}