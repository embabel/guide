package com.embabel.hub.integrations

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts and decrypts API keys using AES-256-GCM.
 *
 * The encryption key is derived from the EMBABEL_KEY_SECRET env var (base64-encoded 32 bytes).
 * If not set, a random key is generated at startup (keys won't survive server restart,
 * but encrypted blobs from the client will simply fail to decrypt and the user re-enters).
 */
@Service
class KeyEncryptionService {

    private val logger = LoggerFactory.getLogger(KeyEncryptionService::class.java)
    private val secretKey: SecretKey
    private val random = SecureRandom()

    init {
        val envKey = System.getenv("EMBABEL_KEY_SECRET")
        secretKey = if (!envKey.isNullOrBlank()) {
            val decoded = Base64.getDecoder().decode(envKey)
            require(decoded.size == 32) { "EMBABEL_KEY_SECRET must be 32 bytes (base64-encoded)" }
            SecretKeySpec(decoded, "AES")
        } else {
            logger.warn("EMBABEL_KEY_SECRET not set — generating ephemeral key (encrypted blobs won't survive restart)")
            val keyBytes = ByteArray(32)
            random.nextBytes(keyBytes)
            SecretKeySpec(keyBytes, "AES")
        }
    }

    /**
     * Encrypts a plaintext API key. Returns a base64 string containing IV + ciphertext.
     */
    fun encrypt(plaintext: String): String {
        val iv = ByteArray(GCM_IV_LENGTH)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // Concatenate IV + ciphertext
        val combined = iv + ciphertext
        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Decrypts a base64 blob back to the plaintext API key.
     * Returns null if decryption fails (e.g. wrong key, corrupted data).
     */
    fun decrypt(encryptedBase64: String): String? {
        return try {
            val combined = Base64.getDecoder().decode(encryptedBase64)
            if (combined.size < GCM_IV_LENGTH) return null
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            logger.debug("Decryption failed (likely stale blob): {}", e.message)
            null
        }
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}