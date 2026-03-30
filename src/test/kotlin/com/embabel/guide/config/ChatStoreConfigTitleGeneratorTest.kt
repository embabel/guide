package com.embabel.guide.config

import com.embabel.agent.spi.support.springai.SpringAiLlmService
import com.embabel.chat.MessageRole
import com.embabel.chat.store.adapter.TitleGenerator
import com.embabel.hub.integrations.LlmProvider
import com.embabel.hub.integrations.UserKeyStore
import com.embabel.hub.integrations.UserModelFactory
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt

class ChatStoreConfigTitleGeneratorTest {

    private val userKeyStore = mock(UserKeyStore::class.java)
    private val userModelFactory = mock(UserModelFactory::class.java)

    private val titleGenerator = ChatStoreConfig().titleGenerator(userKeyStore, userModelFactory)

    /** Minimal [com.embabel.chat.Message] for passing to generate(). */
    private fun userMessage(text: String) = object : com.embabel.chat.Message {
        override val role = MessageRole.USER
        override val content = text
        override val timestamp = java.time.Instant.now()
    }

    @Test
    fun `returns DEFAULT_FALLBACK when userId is null`() {
        val result = runBlocking {
            titleGenerator.generate(listOf(userMessage("Hello")), currentTitle = null, userId = null)
        }
        assertEquals(TitleGenerator.DEFAULT_FALLBACK, result)
        verifyNoInteractions(userKeyStore, userModelFactory)
    }

    @Test
    fun `returns DEFAULT_FALLBACK when user has no API key`() {
        `when`(userKeyStore.getActiveKey("user-1")).thenReturn(null)

        val result = runBlocking {
            titleGenerator.generate(listOf(userMessage("Hello")), currentTitle = null, userId = "user-1")
        }
        assertEquals(TitleGenerator.DEFAULT_FALLBACK, result)
        verify(userKeyStore).getActiveKey("user-1")
        verifyNoInteractions(userModelFactory)
    }

    @Test
    fun `returns DEFAULT_FALLBACK when chatModel returns null text`() {
        val chatModel = mock(ChatModel::class.java)
        val assistantMessage = mock(AssistantMessage::class.java)
        val generation = mock(Generation::class.java)
        val chatResponse = mock(ChatResponse::class.java)

        `when`(userKeyStore.getActiveKey("user-1")).thenReturn(Pair(LlmProvider.OPENAI, "sk-test"))
        `when`(userModelFactory.getLlmService(LlmProvider.OPENAI, LlmProvider.OPENAI.summarizerModel, "sk-test"))
            .thenReturn(SpringAiLlmService(name = "test", provider = "openai", chatModel = chatModel))
        `when`(chatModel.call(any(Prompt::class.java))).thenReturn(chatResponse)
        `when`(chatResponse.result).thenReturn(generation)
        `when`(generation.output).thenReturn(assistantMessage)
        `when`(assistantMessage.text).thenReturn(null)

        val result = runBlocking {
            titleGenerator.generate(listOf(userMessage("Hello")), currentTitle = null, userId = "user-1")
        }
        assertEquals(TitleGenerator.DEFAULT_FALLBACK, result)
    }

    @Test
    fun `calls summarizerModel for the provider`() {
        val chatModel = mock(ChatModel::class.java)
        val assistantMessage = mock(AssistantMessage::class.java)
        val generation = mock(Generation::class.java)
        val chatResponse = mock(ChatResponse::class.java)

        `when`(userKeyStore.getActiveKey("user-1")).thenReturn(Pair(LlmProvider.ANTHROPIC, "sk-ant-test"))
        `when`(
            userModelFactory.getLlmService(
                LlmProvider.ANTHROPIC,
                LlmProvider.ANTHROPIC.summarizerModel,
                "sk-ant-test"
            )
        ).thenReturn(SpringAiLlmService(name = "test", provider = "anthropic", chatModel = chatModel))
        `when`(chatModel.call(any(Prompt::class.java))).thenReturn(chatResponse)
        `when`(chatResponse.result).thenReturn(generation)
        `when`(generation.output).thenReturn(assistantMessage)
        `when`(assistantMessage.text).thenReturn("Some title")

        runBlocking {
            titleGenerator.generate(listOf(userMessage("Hello")), currentTitle = null, userId = "user-1")
        }

        verify(userModelFactory).getLlmService(
            LlmProvider.ANTHROPIC,
            LlmProvider.ANTHROPIC.summarizerModel,
            "sk-ant-test"
        )
    }

    @Test
    fun `returns generated title when all succeeds`() {
        val chatModel = mock(ChatModel::class.java)
        val assistantMessage = mock(AssistantMessage::class.java)
        val generation = mock(Generation::class.java)
        val chatResponse = mock(ChatResponse::class.java)

        `when`(userKeyStore.getActiveKey("user-1")).thenReturn(Pair(LlmProvider.OPENAI, "sk-test"))
        `when`(userModelFactory.getLlmService(LlmProvider.OPENAI, LlmProvider.OPENAI.summarizerModel, "sk-test"))
            .thenReturn(SpringAiLlmService(name = "test", provider = "openai", chatModel = chatModel))
        `when`(chatModel.call(any(Prompt::class.java))).thenReturn(chatResponse)
        `when`(chatResponse.result).thenReturn(generation)
        `when`(generation.output).thenReturn(assistantMessage)
        `when`(assistantMessage.text).thenReturn("My Generated Title")

        val result = runBlocking {
            titleGenerator.generate(listOf(userMessage("Tell me about Kotlin")), currentTitle = null, userId = "user-1")
        }
        assertEquals("My Generated Title", result)
    }
}