package com.embabel.guide.migration

import com.embabel.guide.chat.service.ChatSessionService
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.domain.PersonaRepository
import com.embabel.guide.stats.GuideStatsService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * After [Neo4jToPostgresMigration]: reads every migrated user back through Guide's own services on Postgres, so a
 * value Drivine cannot map back (a temporal, a list, a stamp) fails here rather than in front of a user. The expected
 * counts are the source's, which the migration prints.
 *
 * It connects as the `postgres` profile does (POSTGRES_HOST, POSTGRES_PORT, POSTGRES_USERNAME, POSTGRES_PASSWORD,
 * POSTGRES_DATABASE). USE_LOCAL_POSTGRES=true is needed: without it the test config starts a Postgres container of
 * its own and reads that.
 *
 *     USE_LOCAL_POSTGRES=true POSTGRES_DATABASE=migrate MIGRATE_EXPECT_SESSIONS=104 MIGRATE_EXPECT_MESSAGES=1161 \
 *     mvn -q test -Dtest=MigratedDataReadBackTest -Dsurefire.failIfNoSpecifiedTests=false
 */
@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "MIGRATE_EXPECT_MESSAGES", matches = "[0-9]+")
class MigratedDataReadBackTest {

    @Autowired lateinit var users: GuideUserRepository
    @Autowired lateinit var sessions: ChatSessionService
    @Autowired lateinit var personas: PersonaRepository
    @Autowired lateinit var stats: GuideStatsService

    @Test
    fun `every migrated user reads back with their sessions and messages`() {
        val all = users.findAll()
        var sessionCount = 0
        var messageCount = 0
        all.forEach { user ->
            personas.findForUser(user.core.id)
            sessions.findByOwnerId(user.core.id).forEach { listed ->
                sessionCount++
                val session = sessions.findBySessionId(listed.session.sessionId).orElseThrow()
                messageCount += session.messages.size
            }
        }
        val ratings = stats.pageRatings()
        println("Users ${all.size}, sessions $sessionCount, messages $messageCount, rated pages ${ratings.size}")
        assertEquals(System.getenv("MIGRATE_EXPECT_SESSIONS").toInt(), sessionCount, "sessions")
        assertEquals(System.getenv("MIGRATE_EXPECT_MESSAGES").toInt(), messageCount, "messages")
    }
}
