package com.embabel.hub.integrations

import com.embabel.hub.integrations.UserKeyStore.Companion.maskKey
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UserKeyStoreTest {

    private val store = UserKeyStore()

    @Test
    fun `getActiveKey returns null for unknown user`() {
        assertNull(store.getActiveKey("nobody"))
    }

    @Test
    fun `setKey stores key and auto-activates first provider`() {
        store.setKey("u1", LlmProvider.OPENAI, "sk-123")
        val (provider, key) = store.getActiveKey("u1")!!
        assertEquals(LlmProvider.OPENAI, provider)
        assertEquals("sk-123", key)
    }

    @Test
    fun `second setKey does not change active provider`() {
        store.setKey("u1", LlmProvider.OPENAI, "sk-123")
        store.setKey("u1", LlmProvider.ANTHROPIC, "sk-ant-456")
        val (provider, _) = store.getActiveKey("u1")!!
        assertEquals(LlmProvider.OPENAI, provider)
    }

    @Test
    fun `setActiveProvider switches active provider`() {
        store.setKey("u1", LlmProvider.OPENAI, "sk-123")
        store.setKey("u1", LlmProvider.ANTHROPIC, "sk-ant-456")
        store.setActiveProvider("u1", LlmProvider.ANTHROPIC)
        val (provider, key) = store.getActiveKey("u1")!!
        assertEquals(LlmProvider.ANTHROPIC, provider)
        assertEquals("sk-ant-456", key)
    }

    @Test
    fun `setActiveProvider throws if provider has no key`() {
        store.setKey("u1", LlmProvider.OPENAI, "sk-123")
        assertThrows<IllegalArgumentException> {
            store.setActiveProvider("u1", LlmProvider.DEEPSEEK)
        }
    }

    @Test
    fun `removeKey removes provider and falls back to another`() {
        store.setKey("u1", LlmProvider.OPENAI, "sk-123")
        store.setKey("u1", LlmProvider.ANTHROPIC, "sk-ant-456")
        store.removeKey("u1", LlmProvider.OPENAI)
        val (provider, key) = store.getActiveKey("u1")!!
        assertEquals(LlmProvider.ANTHROPIC, provider)
        assertEquals("sk-ant-456", key)
    }

    @Test
    fun `removeKey last provider clears everything`() {
        store.setKey("u1", LlmProvider.OPENAI, "sk-123")
        store.removeKey("u1", LlmProvider.OPENAI)
        assertNull(store.getActiveKey("u1"))
        assertNull(store.getConfig("u1"))
    }

    @Test
    fun `setKey overwrites existing key for same provider`() {
        store.setKey("u1", LlmProvider.OPENAI, "old-key")
        store.setKey("u1", LlmProvider.OPENAI, "new-key")
        val (_, key) = store.getActiveKey("u1")!!
        assertEquals("new-key", key)
    }

    @Test
    fun `getProviders lists all providers with correct status`() {
        store.setKey("u1", LlmProvider.OPENAI, "sk-123")
        store.setKey("u1", LlmProvider.ANTHROPIC, "sk-ant-456")
        val statuses = store.getProviders("u1")
        assertEquals(LlmProvider.entries.size, statuses.size)

        val openai = statuses.first { it.provider == LlmProvider.OPENAI }
        assertTrue(openai.configured)
        assertTrue(openai.active)
        assertEquals("${"•".repeat(32)}-123", openai.keyHint)

        val anthropic = statuses.first { it.provider == LlmProvider.ANTHROPIC }
        assertTrue(anthropic.configured)
        assertFalse(anthropic.active)
        assertEquals("${"•".repeat(32)}-456", anthropic.keyHint)

        val deepseek = statuses.first { it.provider == LlmProvider.DEEPSEEK }
        assertFalse(deepseek.configured)
        assertFalse(deepseek.active)
        assertNull(deepseek.keyHint)
    }

    @Test
    fun `getProviders for unknown user shows all unconfigured`() {
        val statuses = store.getProviders("nobody")
        assertTrue(statuses.all { !it.configured && !it.active })
    }

    @Test
    fun `maskKey shows only last 4 characters`() {
        assertEquals("${"•".repeat(32)}x789", "sk-proj-abcdx789".maskKey())
    }

    @Test
    fun `maskKey masks short keys entirely`() {
        assertEquals("•".repeat(32), "abcd".maskKey())
        assertEquals("•".repeat(32), "abc".maskKey())
    }

    @Test
    fun `users are isolated`() {
        store.setKey("u1", LlmProvider.OPENAI, "key1")
        store.setKey("u2", LlmProvider.ANTHROPIC, "key2")
        assertEquals(LlmProvider.OPENAI, store.getActiveKey("u1")!!.first)
        assertEquals(LlmProvider.ANTHROPIC, store.getActiveKey("u2")!!.first)
    }
}