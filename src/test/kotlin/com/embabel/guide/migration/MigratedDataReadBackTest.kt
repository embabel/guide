package com.embabel.guide.migration

import com.embabel.guide.chat.service.ChatSessionService
import com.embabel.guide.domain.FeedbackView
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.domain.PersonaRepository
import com.embabel.guide.domain.PersonaData
import com.embabel.guide.domain.loadAll
import org.drivine.manager.StatelessGraphObjectManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * After [Neo4jToPostgresMigration]: reads the migrated data back through Guide's own services on Postgres, so a value
 * Drivine cannot map back (a temporal, a list, a stamp) fails here rather than in front of a user. Each user is
 * loaded with the persona they use, their sessions with their messages, and the personas they may choose; every
 * persona and every piece of feedback is too.
 * The expected counts are the source's, which the migration prints (ChatSession, StoredMessage, Persona, Feedback).
 *
 * It connects as the `postgres` profile does (POSTGRES_HOST, POSTGRES_PORT, POSTGRES_USERNAME, POSTGRES_PASSWORD,
 * POSTGRES_DATABASE). USE_LOCAL_POSTGRES=true is needed: without it the test config starts a Postgres container of
 * its own and reads that.
 *
 * It starts the whole app, so Guide's start-up work runs first: it makes the bot and the system personas if they
 * are missing. The migration has already checked they were copied; a persona count above the source's means one
 * was made here.
 *
 *     USE_LOCAL_POSTGRES=true POSTGRES_DATABASE=migrate MIGRATE_EXPECT_SESSIONS=104 MIGRATE_EXPECT_MESSAGES=1161 \
 *     MIGRATE_EXPECT_PERSONAS=93 MIGRATE_EXPECT_FEEDBACK=48 \
 *     mvn -q test -Dtest=MigratedDataReadBackTest -Dsurefire.failIfNoSpecifiedTests=false
 */
@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "MIGRATE_EXPECT_MESSAGES", matches = "[0-9]+")
class MigratedDataReadBackTest {

    @Autowired lateinit var users: GuideUserRepository
    @Autowired lateinit var sessions: ChatSessionService
    @Autowired lateinit var personas: PersonaRepository

    @Autowired @Qualifier("neoGraphObjectManager")
    lateinit var graphObjectManager: StatelessGraphObjectManager

    private fun expected(name: String) = requireNotNull(System.getenv(name)?.toIntOrNull()) { "$name is not set to a count" }

    @Test
    fun `every migrated user reads back with their sessions, messages and personas`() {
        val all = users.findAll()
        var sessionCount = 0
        var messageCount = 0
        all.forEach { user ->
            personas.findForUser(user.core.id)
            sessions.findByOwnerId(user.core.id).forEach { session ->
                sessionCount++
                messageCount += session.messages.size
            }
        }
        // As nodes: most system personas in prod have lost their owner, so do not load as a PersonaView.
        val personaCount = graphObjectManager.loadAll<PersonaData> { }.size
        val feedbackCount = graphObjectManager.loadAll<FeedbackView> { }.size
        println("Users ${all.size}, sessions $sessionCount, messages $messageCount, personas $personaCount, feedback $feedbackCount")
        assertEquals(expected("MIGRATE_EXPECT_SESSIONS"), sessionCount, "sessions")
        assertEquals(expected("MIGRATE_EXPECT_MESSAGES"), messageCount, "messages")
        assertEquals(expected("MIGRATE_EXPECT_PERSONAS"), personaCount, "personas")
        assertEquals(expected("MIGRATE_EXPECT_FEEDBACK"), feedbackCount, "feedback")
    }
}
