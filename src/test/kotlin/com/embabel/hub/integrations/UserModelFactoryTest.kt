package com.embabel.hub.integrations

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration

class UserModelFactoryTest {

    private val factory = UserModelFactory()

    @Test
    fun `same provider, model, and key returns cached instance`() {
        val s1 = factory.getLlmService(LlmProvider.OPENAI, "gpt-4.1", "sk-test-key")
        val s2 = factory.getLlmService(LlmProvider.OPENAI, "gpt-4.1", "sk-test-key")
        assertSame(s1, s2)
    }

    @Test
    fun `different key returns different instance`() {
        val s1 = factory.getLlmService(LlmProvider.OPENAI, "gpt-4.1", "sk-key-1")
        val s2 = factory.getLlmService(LlmProvider.OPENAI, "gpt-4.1", "sk-key-2")
        assertNotSame(s1, s2)
    }

    @Test
    fun `different model returns different instance`() {
        val s1 = factory.getLlmService(LlmProvider.OPENAI, "gpt-4.1", "sk-same")
        val s2 = factory.getLlmService(LlmProvider.OPENAI, "gpt-4.1-mini", "sk-same")
        assertNotSame(s1, s2)
    }

    @Test
    fun `different provider returns different instance`() {
        val s1 = factory.getLlmService(LlmProvider.OPENAI, "gpt-4.1", "sk-same")
        val s2 = factory.getLlmService(LlmProvider.ANTHROPIC, "claude-sonnet-4-20250514", "sk-same")
        assertNotSame(s1, s2)
    }

    @Test
    fun `creates service for each provider`() {
        factory.getLlmService(LlmProvider.OPENAI, "gpt-4.1", "sk-openai")
        factory.getLlmService(LlmProvider.ANTHROPIC, "claude-sonnet-4-20250514", "sk-anthropic")
        factory.getLlmService(LlmProvider.MISTRAL, "mistral-large-latest", "mistral-key")
        factory.getLlmService(LlmProvider.DEEPSEEK, "deepseek-chat", "ds-key")
        assertEquals(4, factory.cacheSize())
    }

    @Test
    fun `stale entries are evicted on next access`() {
        val shortTtl = UserModelFactory(ttl = Duration.ofMillis(50))
        shortTtl.getLlmService(LlmProvider.OPENAI, "gpt-4.1", "sk-stale")
        assertEquals(1, shortTtl.cacheSize())

        Thread.sleep(100)

        // Next access triggers eviction of the stale entry, then creates a new one
        shortTtl.getLlmService(LlmProvider.ANTHROPIC, "claude-sonnet-4-20250514", "sk-fresh")
        assertEquals(1, shortTtl.cacheSize())
    }

    @Test
    fun `recently used entries survive eviction`() {
        val shortTtl = UserModelFactory(ttl = Duration.ofMillis(200))
        shortTtl.getLlmService(LlmProvider.OPENAI, "gpt-4.1", "sk-1")
        shortTtl.getLlmService(LlmProvider.ANTHROPIC, "claude-sonnet-4-20250514", "sk-2")
        assertEquals(2, shortTtl.cacheSize())

        Thread.sleep(120)

        // Touch only the OpenAI entry to refresh its lastUsed
        shortTtl.getLlmService(LlmProvider.OPENAI, "gpt-4.1", "sk-1")

        Thread.sleep(120)

        // Anthropic is now >200ms stale, OpenAI was refreshed ~120ms ago
        shortTtl.getLlmService(LlmProvider.OPENAI, "gpt-4.1", "sk-1")
        assertEquals(1, shortTtl.cacheSize())
    }
}