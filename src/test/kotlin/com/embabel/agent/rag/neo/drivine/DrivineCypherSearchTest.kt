package com.embabel.agent.rag.neo.drivine

import com.embabel.agent.rag.model.Chunk
import com.embabel.agent.rag.model.ContentElement
import com.embabel.agent.rag.service.ClusterRetrievalRequest
import com.embabel.agent.rag.service.ContentElementSearch
import com.embabel.agent.rag.service.EntitySearch
import com.embabel.guide.Neo4jPropertiesInitializer
import org.drivine.manager.PersistenceManager
import org.drivine.query.QuerySpecification
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
@ImportAutoConfiguration(exclude = [McpClientAutoConfiguration::class])
class DrivineCypherSearchTest(
    @Autowired private val search: DrivineCypherSearch,
    @Autowired private val properties: NeoRagServiceProperties,
    @Autowired @Qualifier("neo") private val persistenceManager: PersistenceManager
) {

    @Nested
    inner class CrudTests {

        @BeforeEach
        fun setup() {
            val spec = QuerySpecification.withStatement("""
                CREATE (n:Foobar {foo: "foo"})
            """.trimIndent())
            persistenceManager.execute(spec)
        }

        @Test
        fun `should query for int`() {
            val result = search.queryForInt("MATCH (n:Foobar) return count(n)")
            assertEquals(result, 1)
        }

    }

    @Nested
    inner class ClusteringTests {

        @BeforeEach
        fun setupTestData() {
        // Clean up any existing test data
        search.query(
            purpose = "Clean test data",
            query = "MATCH (n:Chunk) WHERE n.id STARTS WITH 'test-chunk-' DETACH DELETE n",
            params = emptyMap<String, Any>()
        )

        // Create test chunks with embeddings
        // We'll create 10 chunks with similar embeddings that should cluster
        val chunks = mutableListOf<Map<String, Any>>()

        // Group 1: 5 chunks with similar embeddings (around [0.5, 0.5, 0.5, ...])
        for (i in 1..5) {
            chunks.add(
                mapOf(
                    "id" to "test-chunk-group1-$i",
                    "text" to "This is test chunk $i from group 1",
                    "parentId" to "test-doc-1",
                    "uri" to "test://doc1",
                    "embedding" to List(1536) { 0.5 + (Random().nextDouble() - 0.5) * 0.1 },
                    "metadataSource" to "test"
                )
            )
        }

        // Group 2: 5 chunks with different embeddings (around [0.8, 0.2, 0.8, ...])
        for (i in 1..5) {
            val embedding = List(1536) { idx ->
                if (idx % 2 == 0) 0.8 + (Random().nextDouble() - 0.5) * 0.1
                else 0.2 + (Random().nextDouble() - 0.5) * 0.1
            }
            chunks.add(
                mapOf(
                    "id" to "test-chunk-group2-$i",
                    "text" to "This is test chunk $i from group 2",
                    "parentId" to "test-doc-2",
                    "uri" to "test://doc2",
                    "embedding" to embedding,
                    "metadataSource" to "test"
                )
            )
        }

        // Insert chunks
        val insertQuery = """
            UNWIND ${'$'}chunks AS chunk
            CREATE (c:Chunk:ContentElement)
            SET c.id = chunk.id,
                c.text = chunk.text,
                c.parentId = chunk.parentId,
                c.uri = chunk.uri,
                c.embedding = chunk.embedding
            SET c += {`metadata.source`: chunk.metadataSource}
        """.trimIndent()

        // Use persistence manager directly to avoid primitiveProps stripping nested maps
        persistenceManager.query(
            org.drivine.query.QuerySpecification
                .withStatement(insertQuery)
                .bind(mapOf("chunks" to chunks))
                .transform(Map::class.java)
        )

        // Create vector index if it doesn't exist
        try {
            search.query(
                purpose = "Create vector index",
                query = """
                    CREATE VECTOR INDEX ${'$'}indexName IF NOT EXISTS
                    FOR (c:Chunk)
                    ON c.embedding
                    OPTIONS {indexConfig: {
                        `vector.dimensions`: 1536,
                        `vector.similarity_function`: 'cosine'
                    }}
                """.trimIndent(),
                params = mapOf("indexName" to properties.contentElementIndex)
            )
        } catch (e: Exception) {
            println("Vector index might already exist: ${e.message}")
        }
    }

        @AfterEach
        fun cleanupTestData() {
            // Clean up test data
            search.query(
                purpose = "Clean test data",
                query = "MATCH (n:Chunk) WHERE n.id STARTS WITH 'test-chunk-' DETACH DELETE n",
                params = emptyMap<String, Any>()
            )
        }

        @Test
        fun `should find clusters` () {
            // Verify test data was created
            val chunkCountQuery = "MATCH (c:Chunk) WHERE c.id STARTS WITH 'test-chunk-' RETURN {count: count(c)} AS result"
            val countResult = search.query(
                purpose = "Count test chunks",
                query = chunkCountQuery,
                params = emptyMap<String, Any>()
            )

            val chunkCount = countResult.firstOrNull()?.get("count") as? Long ?: 0L
            println("Number of test Chunk nodes: $chunkCount")
            assertTrue(chunkCount == 10L, "Expected 10 test chunks, found $chunkCount")

            // Create a cluster retrieval request for Chunks
            val request = ClusterRetrievalRequest<Chunk>(
                vectorIndex = properties.contentElementIndex,
                topK = 5,
                similarityThreshold = 0.7,
                entitySearch = EntitySearch(labels = setOf("Chunk")),
                contentElementSearch = ContentElementSearch(types = listOf(Chunk::class.java))
            )

            val clusters = search.findClusters(request)

            assertNotNull(clusters)
            println("Found ${clusters.size} clusters")
            assertTrue(clusters.isNotEmpty(), "Expected to find at least one cluster")

            clusters.forEach { cluster ->
                println("Cluster anchor: ${(cluster.anchor as ContentElement).id}")
                println("  Similar items: ${cluster.similar.size}")
                cluster.similar.forEach { similar ->
                    println("    - ${(similar.match as ContentElement).id} (score: ${similar.score})")
                }
            }
        }
    }
}
