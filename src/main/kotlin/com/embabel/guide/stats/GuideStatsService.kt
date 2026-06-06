package com.embabel.guide.stats

import com.embabel.chat.store.model.MessageData
import com.embabel.guide.chat.service.PresenceService
import com.embabel.guide.domain.GuideUserCache
import com.embabel.guide.domain.GuideUserData
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.rag.DataManager
import org.drivine.manager.GraphObjectManager
import org.drivine.manager.PersistenceManager
import org.drivine.query.QuerySpecification
import org.drivine.query.transform
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * Assembles the `GET /api/v1/data/stats` payload. Everyone gets the content counts; the
 * people/message figures are added only when the caller holds the ADMIN role.
 *
 * Admin status is read from the caller's `roles` (granted out-of-band via Cypher). The lookup
 * reuses [GuideUserCache]; its TTL is what makes an out-of-band grant/revocation take effect.
 *
 * Totals use single-label fragment counts. `topUsersThisWeek` is a time-filtered, two-hop,
 * group-by ranking — not expressible as a node-rooted view's @Count/@Aggregate — so it uses
 * [PersistenceManager] + `.transform<T>()`, per Drivine's own guidance.
 */
@Service
class GuideStatsService(
    private val dataManager: DataManager,
    private val presenceService: PresenceService,
    private val guideUserCache: GuideUserCache,
    private val guideUserRepository: GuideUserRepository,
    @param:Qualifier("neoGraphObjectManager") private val graphObjectManager: GraphObjectManager,
    @param:Qualifier("neo") private val persistenceManager: PersistenceManager,
) {

    /**
     * @param webUserId the authenticated caller (JWT subject), or null for anonymous/unauthenticated.
     */
    @Transactional(readOnly = true)
    fun stats(webUserId: String?): GuideStats {
        val content = dataManager.stats
        if (!isAdmin(webUserId)) {
            return GuideStats(content = content)
        }
        return GuideStats(
            content = content,
            userCount = graphObjectManager.count(GuideUserData::class.java),
            onlineCount = presenceService.onlineUsers().size,
            messageCount = graphObjectManager.count(MessageData::class.java),
            topUsersThisWeek = topMessagingUsers(since = Instant.now().minus(Duration.ofDays(7))),
        )
    }

    private fun isAdmin(webUserId: String?): Boolean {
        if (webUserId.isNullOrBlank()) return false
        val user = guideUserCache.get(webUserId)
            ?: guideUserRepository.findByWebUserId(webUserId).orElse(null)
                ?.also { guideUserCache.put(webUserId, it) }
            ?: return false
        return ADMIN_ROLE in user.core.roles
    }

    /** Top users by USER-role messages sent in their sessions since [since]. */
    private fun topMessagingUsers(since: Instant): List<TopMessagingUser> =
        persistenceManager.query(
            QuerySpecification
                .withStatement(
                    """
                    MATCH (u:GuideUser)<-[:OWNED_BY]-(:ChatSession)-[:HAS_MESSAGE]->(m:StoredMessage)
                    // datetime() parses createdAt: drivine currently persists Instant as an ISO
                    // STRING, so a bare `>=` against a temporal param silently matches nothing.
                    // Idempotent if drivine is later fixed to store native temporals.
                    WHERE m.role = 'USER' AND datetime(m.createdAt) >= ${'$'}since
                    WITH u, count(m) AS messageCount
                    ORDER BY messageCount DESC
                    LIMIT $TOP_USERS_LIMIT
                    // Single-map projection: .transform<T>() maps one map/scalar per row, not multiple columns.
                    RETURN { displayName: coalesce(u.username, u.id), messageCount: messageCount } AS user
                    """.trimIndent()
                )
                .bind(mapOf("since" to since)) // Instant -> ZonedDateTime, matching how createdAt is stored
                .transform<TopMessagingUser>()
        )

    companion object {
        const val ADMIN_ROLE = "ADMIN"
        const val TOP_USERS_LIMIT = 5
    }
}