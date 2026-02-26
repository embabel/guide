package com.embabel.guide.narrator

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight cache bridging narration production (ChatActions) and consumption
 * (MessageEventListener for both WebSocket delivery and DB persistence).
 *
 * Lifecycle for a single message:
 * 1. ChatActions computes narration → [put]
 * 2. ADDED event handler reads narration for WebSocket delivery → [consumeForDelivery] (peek)
 * 3. PERSISTED event handler reads narration for DB update → [consumeForPersistence] (remove)
 */
@Component
class NarrationCache {

    private val logger = LoggerFactory.getLogger(NarrationCache::class.java)
    private val cache = ConcurrentHashMap<String, String>()

    /**
     * Store narration for a conversation. Called from the agent thread in ChatActions.
     */
    fun put(conversationId: String, narration: String) {
        logger.info("[NARRATION] Cache PUT conversationId={}, length={}, cacheSize={}", conversationId, narration.length, cache.size + 1)
        cache[conversationId] = narration
    }

    /**
     * Read narration without removing it (needed for DB persistence later).
     * Called from the ADDED event handler.
     */
    fun consumeForDelivery(conversationId: String): String? {
        val result = cache[conversationId]
        logger.info("[NARRATION] Cache CONSUME_DELIVERY conversationId={}, found={}", conversationId, result != null)
        return result
    }

    /**
     * Read and remove narration. Called from the PERSISTED event handler.
     */
    fun consumeForPersistence(conversationId: String): String? {
        val result = cache.remove(conversationId)
        logger.info("[NARRATION] Cache CONSUME_PERSIST conversationId={}, found={}", conversationId, result != null)
        return result
    }
}
