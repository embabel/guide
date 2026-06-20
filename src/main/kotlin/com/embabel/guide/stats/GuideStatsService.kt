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
        // Page rankings are public: split the eligible pages so the weightier side decides the list
        // (ties land in neither). Both lists go to everyone, admin or not.
        val ratings = pageRatings()
        val popularPages = ratings.filter { it.likes > it.dislikes }
            .sortedByDescending { it.likes }.take(TOP_PAGES_LIMIT)
        val unpopularPages = ratings.filter { it.dislikes > it.likes }
            .sortedByDescending { it.dislikes }.take(TOP_PAGES_LIMIT)
        if (!isAdmin(webUserId)) {
            return GuideStats(content = content, popularPages = popularPages, unpopularPages = unpopularPages)
        }
        return GuideStats(
            content = content,
            popularPages = popularPages,
            unpopularPages = unpopularPages,
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
                    // datetime() wrap tolerates legacy rows where createdAt was persisted as an ISO
                    // STRING (drivine <= 0.0.44). drivine 0.0.45 writes native temporals, and the
                    // 06-migrate-string-dates.cypher seed converts old rows — once that has run in all
                    // environments this wrap is redundant (idempotent on a native datetime) and may be
                    // dropped, ideally alongside a range index on StoredMessage.createdAt.
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

    /**
     * Every page with at least [FEEDBACK_MIN_VOTES] total votes, with its like/dislike tallies.
     * Filtering by total volume here keeps a page with a single vote off the rankings; the caller
     * decides which side a page falls on and how many to show.
     */
    private fun pageRatings(): List<PageRating> =
        persistenceManager.query(
            QuerySpecification
                .withStatement(
                    """
                    MATCH (f:Feedback)
                    WITH f.page AS page,
                         sum(CASE WHEN f.helpful THEN 1 ELSE 0 END) AS likes,
                         sum(CASE WHEN f.helpful THEN 0 ELSE 1 END) AS dislikes
                    WHERE likes + dislikes >= ${'$'}minVotes
                    // Single-map projection: .transform<T>() maps one map/scalar per row, not multiple columns.
                    RETURN { page: page, likes: likes, dislikes: dislikes } AS row
                    """.trimIndent()
                )
                .bind(mapOf("minVotes" to FEEDBACK_MIN_VOTES))
                .transform<PageRating>()
        )

    companion object {
        const val ADMIN_ROLE = "ADMIN"
        const val TOP_USERS_LIMIT = 5
        const val TOP_PAGES_LIMIT = 5
        const val FEEDBACK_MIN_VOTES = 5
    }
}