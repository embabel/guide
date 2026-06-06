package com.embabel.guide.domain

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache for GuideUser lookups, keyed by webUserId.
 * Eliminates repeated Neo4j reads for the same user within and across requests.
 *
 * Entries carry a TTL ([ttlSeconds]). On read, an expired entry is evicted and treated
 * as a miss so the caller reloads from the repository. The TTL exists because some user
 * state is changed out-of-band (e.g. `roles` granted via a raw Cypher statement) and so
 * never triggers [invalidate]; the TTL bounds how stale such a change can be — it is the
 * effective propagation/revocation window for ADMIN role grants.
 *
 * Invalidate explicitly on any in-app write that changes user state visible to ChatActions:
 * persona, customPrompt, welcomed flag.
 */
@Service
class GuideUserCache(
    @Value("\${guide.user-cache.ttl-seconds:300}") ttlSeconds: Long
) {

    private val logger = LoggerFactory.getLogger(GuideUserCache::class.java)
    private val ttl: Duration = Duration.ofSeconds(ttlSeconds)
    private val cache = ConcurrentHashMap<String, Entry>()
    private val byInternalId = ConcurrentHashMap<String, Entry>()

    private data class Entry(val user: GuideUser, val expiresAt: Instant) {
        fun isExpired(now: Instant): Boolean = now.isAfter(expiresAt)
    }

    fun get(key: String): GuideUser? = read(cache, key)

    fun getByInternalId(internalId: String): GuideUser? = read(byInternalId, internalId)

    fun put(key: String, user: GuideUser) {
        val entry = Entry(user, Instant.now().plus(ttl))
        cache[key] = entry
        byInternalId[user.core.id] = entry
    }

    fun invalidate(key: String) {
        val entry = cache.remove(key)
        entry?.let { byInternalId.remove(it.user.core.id) }
        logger.debug("Invalidated GuideUser cache for key {}", key)
    }

    /** Returns the cached user, or null on miss/expiry — evicting the expired entry so the caller reloads. */
    private fun read(index: ConcurrentHashMap<String, Entry>, key: String): GuideUser? {
        val entry = index[key] ?: return null
        if (entry.isExpired(Instant.now())) {
            index.remove(key, entry)
            return null
        }
        return entry.user
    }
}