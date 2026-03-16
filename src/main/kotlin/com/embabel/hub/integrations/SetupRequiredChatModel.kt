package com.embabel.hub.integrations

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.DefaultChatOptions
import org.springframework.ai.chat.prompt.Prompt

/**
 * A no-op [ChatModel] used as a safety net in cloud/hub deployments where no server-side keys are configured.
 * Returns an empty response — callers should short-circuit before reaching this.
 * When a user provides their own key via BYOK, [UserModelFactory] creates a real model
 * and bypasses this one entirely via `withLlmService()`.
 */
class SetupRequiredChatModel : ChatModel {

    override fun call(prompt: Prompt): ChatResponse {
        val message = AssistantMessage("")
        return ChatResponse(listOf(Generation(message)))
    }

    override fun getDefaultOptions(): ChatOptions = DefaultChatOptions()

    companion object {
        const val MODEL_NAME = "setup-required"

        const val SETUP_MESSAGE = """I'd love to help, but I need you to configure your AI provider first.

Go to **Settings → Integrations** and add your API key for one of these providers:
- **OpenAI** — get a key at https://platform.openai.com/api-keys
- **Anthropic** — get a key at https://console.anthropic.com/settings/keys
- **Mistral** — get a key at https://console.mistral.ai/api-keys
- **DeepSeek** — get a key at https://platform.deepseek.com/api_keys

Once configured, I'll be fully operational!"""
    }
}