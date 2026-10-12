package com.embabel.guide.migration

import com.embabel.guide.Neo4jPropertiesInitializer
import org.drivine.manager.PersistenceManager
import org.drivine.query.QuerySpecification
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.util.UUID

/**
 * Not transactional: the repair commits each batch itself and is refused inside a transaction.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
@ImportAutoConfiguration(exclude = [McpClientAutoConfiguration::class])
class RelationshipRepairRunnerTest {

    @Autowired
    @Qualifier("neo")
    private lateinit var persistenceManager: PersistenceManager

    private val userId = "repair-user-${UUID.randomUUID()}"
    private val feedbackId = "repair-feedback-${UUID.randomUUID()}"

    @AfterEach
    fun removeNodes() {
        execute("MATCH (n) WHERE n.id IN [\$userId, \$feedbackId] DETACH DELETE n")
    }

    @Test
    fun `report leaves a wrong-way GAVE where it is`() {
        saveFeedbackTheOldWay()

        runner("report").run(DefaultApplicationArguments())

        assertEquals(1, count("MATCH (:Feedback {id: \$feedbackId})-[r:GAVE]->(:GuideUser {id: \$userId})"))
    }

    @Test
    fun `repair turns a wrong-way GAVE round, from the user to the feedback`() {
        saveFeedbackTheOldWay()

        runner("repair", "FeedbackView.user").run(DefaultApplicationArguments())

        assertEquals(0, count("MATCH (:Feedback {id: \$feedbackId})-[r:GAVE]->(:GuideUser {id: \$userId})"))
        assertEquals(1, count("MATCH (:GuideUser {id: \$userId})-[r:GAVE]->(:Feedback {id: \$feedbackId})"))
    }

    @Test
    fun `repair refuses a field the views do not have`() {
        assertThrows(IllegalArgumentException::class.java) {
            runner("repair", "FeedbackView.nobody").run(DefaultApplicationArguments())
        }
    }

    private fun runner(mode: String, vararg fields: String) =
        RelationshipRepairRunner(persistenceManager, mode, fields.toList())

    /** What a view save before Drivine 0.1.0 wrote for the INCOMING field: from the root to the target. */
    private fun saveFeedbackTheOldWay() = execute(
        """
        CREATE (f:Feedback {id: ${'$'}feedbackId, page: '/', helpful: true})
        CREATE (u:GuideUser:User {id: ${'$'}userId})
        CREATE (f)-[:GAVE]->(u)
        """.trimIndent()
    )

    private fun execute(cypher: String) =
        persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(ids()))

    private fun count(pattern: String): Long =
        persistenceManager.getOne(
            QuerySpecification.withStatement("$pattern RETURN count(r)").bind(ids()).transform(Long::class.java)
        )

    private fun ids() = mapOf("userId" to userId, "feedbackId" to feedbackId)
}
