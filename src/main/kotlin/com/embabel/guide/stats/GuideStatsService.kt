package com.embabel.guide.stats

import com.embabel.chat.store.model.MessageData
import com.embabel.guide.chat.service.PresenceService
import com.embabel.guide.domain.GuideUserCache
import com.embabel.guide.domain.GuideUserData
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.rag.DataManager
import com.embabel.chat.MessageRole
import com.embabel.chat.store.model.MessageDataQueryDsl
import com.embabel.guide.domain.FeedbackData
import com.embabel.guide.domain.load
import org.drivine.annotation.Direction
import org.drivine.annotation.Hop
import org.drivine.manager.StatelessGraphObjectManager
import org.drivine.query.dsl.query
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
 * Totals use single-label fragment counts. The rankings are Drivine grouped reads: one statement
 * each, on every store Drivine supports, Oracle among them.
 */
@Service
class GuideStatsService(
    private val dataManager: DataManager,
    private val presenceService: PresenceService,
    private val guideUserCache: GuideUserCache,
    private val guideUserRepository: GuideUserRepository,
    @param:Qualifier("neoGraphObjectManager") private val graphObjectManager: StatelessGraphObjectManager,
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

    /** Top [limit] users by USER-role messages sent in their sessions since [since]. */
    internal fun topMessagingUsers(since: Instant, limit: Int = TOP_USERS_LIMIT): List<TopMessagingUser> =
        graphObjectManager.groups.load(MessageData::class.java, MessageDataQueryDsl.INSTANCE) {
            where {
                query.role eq MessageRole.USER
                query.createdAt gte since
            }
            val user = key(path<GuideUserData>(
                Hop("HAS_MESSAGE", Direction.INCOMING, label = "ChatSession"),
                Hop("OWNED_BY"),
            ))
            val messages = count()
            orderBy { messages.desc() }
            limit(limit)
            rows { TopMessagingUser(displayName = it[user].username.ifBlank { it[user].id }, messageCount = it[messages]) }
        }

    /**
     * Every page with at least [FEEDBACK_MIN_VOTES] total votes, with its like/dislike tallies.
     * Filtering by total volume here keeps a page with a single vote off the rankings; the caller
     * decides which side a page falls on and how many to show.
     */
    internal fun pageRatings(): List<PageRating> =
        graphObjectManager.groups.load<FeedbackData, _> {
            val page = key(query.page)
            val votes = count()
            val likes = count { query.helpful eq true }
            having { votes gte FEEDBACK_MIN_VOTES }
            rows { PageRating(page = it[page], likes = it[likes], dislikes = it[votes] - it[likes]) }
        }

    companion object {
        const val ADMIN_ROLE = "ADMIN"
        const val TOP_USERS_LIMIT = 5
        const val TOP_PAGES_LIMIT = 5
        const val FEEDBACK_MIN_VOTES = 5
    }
}