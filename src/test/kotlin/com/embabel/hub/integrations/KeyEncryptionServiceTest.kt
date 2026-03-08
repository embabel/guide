package com.embabel.hub.integrations

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class KeyEncryptionServiceTest {

    private val service = KeyEncryptionService()

    @Test
    fun `encrypt and decrypt round-trips successfully`() {
        val plaintext = "sk-proj-abc123xyz"
        val encrypted = service.encrypt(plaintext)
        val decrypted = service.decrypt(encrypted)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `each encryption produces different ciphertext`() {
        val plaintext = "sk-proj-abc123xyz"
        val a = service.encrypt(plaintext)
        val b = service.encrypt(plaintext)
        assertNotEquals(a, b, "Different IVs should produce different ciphertext")
        assertEquals(plaintext, service.decrypt(a))
        assertEquals(plaintext, service.decrypt(b))
    }

    @Test
    fun `decrypt returns null for garbage input`() {
        assertNull(service.decrypt("not-valid-base64!!!"))
    }

    @Test
    fun `decrypt returns null for truncated ciphertext`() {
        assertNull(service.decrypt("AAAA"))
    }

    @Test
    fun `decrypt returns null for blob from different key`() {
        val other = KeyEncryptionService()
        val encrypted = other.encrypt("sk-secret")
        assertNull(service.decrypt(encrypted))
    }

    @Test
    fun `handles empty string`() {
        val encrypted = service.encrypt("")
        assertEquals("", service.decrypt(encrypted))
    }

    @Test
    fun `handles long keys`() {
        val longKey = "sk-proj-" + "a".repeat(500)
        val encrypted = service.encrypt(longKey)
        assertEquals(longKey, service.decrypt(encrypted))
    }
}