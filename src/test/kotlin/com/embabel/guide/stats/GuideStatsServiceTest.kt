package com.embabel.guide.stats

import com.embabel.chat.MessageRole
import com.embabel.chat.store.model.MessageData
import com.embabel.chat.store.repository.ChatSessionRepository
import com.embabel.guide.Neo4jPropertiesInitializer
import com.embabel.guide.domain.FeedbackData
import com.embabel.guide.domain.GuideUser
import com.embabel.guide.domain.GuideUserData
import com.embabel.guide.domain.PersonaData
import com.embabel.guide.domain.GuideUserRepositoryDefaultImpl
import org.drivine.manager.StatelessGraphObjectManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** The rankings of the stats endpoint, read from the graph. */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
@ImportAutoConfiguration(exclude = [McpClientAutoConfiguration::class])
@Transactional
class GuideStatsServiceTest {

    @Autowired
    private lateinit var statsService: GuideStatsService

    @Autowired
    private lateinit var chatSessionRepository: ChatSessionRepository

    @Autowired
    private lateinit var guideUserRepository: GuideUserRepositoryDefaultImpl

    @Autowired
    @Qualifier("neoGraphObjectManager")
    private lateinit var graphObjectManager: StatelessGraphObjectManager

    @Test
    fun `a page is rated once it has enough votes, with its likes and dislikes`() {
        val liked = page()
        val disliked = page()
        val tooFew = page()
        votes(liked, likes = 4, dislikes = 1)
        votes(disliked, likes = 1, dislikes = 4)
        votes(tooFew, likes = 2, dislikes = 1)

        val ratings = statsService.pageRatings().associateBy { it.page }

        assertEquals(PageRating(liked, likes = 4, dislikes = 1), ratings[liked])
        assertEquals(PageRating(disliked, likes = 1, dislikes = 4), ratings[disliked])
        assertNull(ratings[tooFew])
    }

    @Test
    fun `users are ranked by the messages they sent this week`() {
        val now = Instant.now()
        val busy = user("busy")
        val quiet = user("quiet")
        session(busy).let { id ->
            repeat(3) { message(id, MessageRole.USER, now) }
            repeat(2) { message(id, MessageRole.ASSISTANT, now) }
        }
        session(quiet).let { id ->
            message(id, MessageRole.USER, now)
            message(id, MessageRole.USER, now.minus(Duration.ofDays(8)))
        }

        val top = statsService.topMessagingUsers(since = now.minus(Duration.ofDays(7)))
            .associate { it.displayName to it.messageCount }

        assertEquals(3L, top[busy.username])
        assertEquals(1L, top[quiet.username])
    }

    private fun page() = "/test/${UUID.randomUUID()}"

    private fun votes(page: String, likes: Int, dislikes: Int) {
        repeat(likes) { graphObjectManager.save(FeedbackData(UUID.randomUUID().toString(), page, helpful = true)) }
        repeat(dislikes) { graphObjectManager.save(FeedbackData(UUID.randomUUID().toString(), page, helpful = false)) }
    }

    private fun user(name: String): GuideUserData {
        val persona = graphObjectManager.save(
            PersonaData(id = UUID.randomUUID().toString(), name = "p", prompt = "p", isSystem = true)
        )
        val user = GuideUserData(id = "stats-${UUID.randomUUID()}", displayName = "$name-${UUID.randomUUID()}")
        guideUserRepository.save(GuideUser(core = user, persona = persona))
        return user
    }

    private fun session(owner: GuideUserData): String =
        UUID.randomUUID().toString().also { chatSessionRepository.createSession(it, owner) }

    private fun message(sessionId: String, role: MessageRole, at: Instant) {
        chatSessionRepository.addMessage(
            sessionId,
            MessageData(messageId = UUID.randomUUID().toString(), role = role, content = "hi", createdAt = at),
        )
    }
}
