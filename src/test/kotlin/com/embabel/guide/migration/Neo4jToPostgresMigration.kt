package com.embabel.guide.migration

import org.drivine.connection.DatabaseType
import org.drivine.connection.JdbcConnectionProvider
import org.drivine.manager.NonTransactionalPersistenceManager
import org.drivine.mapper.SubtypeRegistry
import org.drivine.query.CypherStatement
import org.drivine.query.QuerySpecification
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.neo4j.driver.AccessMode
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase
import org.neo4j.driver.SessionConfig
import org.postgresql.ds.PGSimpleDataSource
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * One-off: copies Guide's app data (users, sessions, messages, personas, feedback) from Neo4j to Postgres.
 *
 * Content (`managedBy = embabel`) is the seed's job and is left alone on both sides, as is `_DrivineSchema`, which
 * Guide writes for itself when it starts. Neo4j is only read. On Postgres, every app node is deleted first (with its
 * edges), then the source's are created through Drivine's Cypher, values bound as parameters so Drivine encodes them
 * as it does for any save. A failed run leaves a part copy; run it again with MIGRATE_REPLACE=true, which a target
 * already holding chats needs. See docs/CUTOVER_TO_POSTGRES.md for where this sits in the cutover.
 *
 * The target must already hold Drivine's tables: start Guide against it once.
 *
 *     MIGRATE_NEO4J_URI=… MIGRATE_NEO4J_USERNAME=… MIGRATE_NEO4J_PASSWORD=… \
 *     MIGRATE_POSTGRES_URL=jdbc:postgresql://localhost:5432/migrate MIGRATE_POSTGRES_USERNAME=guide MIGRATE_POSTGRES_PASSWORD=… \
 *     mvn -q test -Dtest=Neo4jToPostgresMigration -Dsurefire.failIfNoSpecifiedTests=false
 */
@EnabledIfEnvironmentVariable(named = "MIGRATE_POSTGRES_URL", matches = ".+")
class Neo4jToPostgresMigration {

    private data class Node(val id: String, val labels: List<String>, val props: Map<String, Any?>)
    private data class Edge(val from: String, val type: String, val to: String, val props: Map<String, Any?>)

    private fun env(name: String) = requireNotNull(System.getenv(name)) { "$name is not set" }

    @Test
    fun migrate() {
        val (nodes, edges, crossing) = read()
        println("Read ${nodes.size} nodes and ${edges.size} edges from Neo4j")

        // Everything that can refuse the source is checked before Postgres is touched.
        check(crossing.isEmpty()) {
            "Edges between app data and content or schema nodes, which neither the seed nor this copy carries: $crossing"
        }
        // Edges go in by SQL (see below), which is only sound while no edge has properties for Drivine to encode.
        val withProps = edges.filter { it.props.isNotEmpty() }.map { it.type }.distinct()
        check(withProps.isEmpty()) { "Edges with properties, which the SQL insert would not encode: $withProps" }

        val ds = PGSimpleDataSource().apply {
            setURL(env("MIGRATE_POSTGRES_URL"))
            user = env("MIGRATE_POSTGRES_USERNAME")
            password = env("MIGRATE_POSTGRES_PASSWORD")
        }
        // Guide's first start makes the bot, the anonymous user and the system personas, which are replaced here.
        // Anything else is someone's data: refuse unless told to replace it, as on a rehearsal rerun.
        val inUse = ds.connection.use { c ->
            c.createStatement().executeQuery("SELECT count(*) FROM drivine_node WHERE ${pgAppNode()} AND NOT ($MADE_ON_START)")
                .use { it.next(); it.getLong(1) }
        }
        check(inUse == 0L || System.getenv("MIGRATE_REPLACE") == "true") {
            "The target already holds $inUse users, logins, chats or feedback, which this would delete: set MIGRATE_REPLACE=true to replace them"
        }
        sql(ds, "DELETE FROM drivine_node WHERE ${pgAppNode()}").also { println("Deleted $it app nodes from Postgres") }

        val registry = SubtypeRegistry()
        val pm = NonTransactionalPersistenceManager(
            JdbcConnectionProvider("migrate", DatabaseType.POSTGRES, ds, registry), "migrate", DatabaseType.POSTGRES, registry,
        )
        fun run(cypher: String, rows: List<Map<String, Any?>>) = rows.chunked(BATCH).forEach {
            pm.execute(QuerySpecification.withStatement(CypherStatement(cypher)).bind(mapOf("rows" to it)))
        }

        timed("nodes") {
            nodes.groupBy { it.labels }.forEach { (labels, group) ->
                run(
                    "UNWIND \$rows AS row CREATE (n${labels.joinToString("") { ":`$it`" }}) SET n += row.props, n.$MARK = row.id",
                    group.map { mapOf("id" to it.id, "props" to it.props) },
                )
            }
        }
        // Edges go in by SQL, joined on the marks in one statement: a Cypher MATCH on a row's value scans every
        // node for each row (drivine4j#617). This writes Drivine's tables directly (drivine_edge, on the default
        // graph), so check it against the Drivine version the cutover ships.
        timed("edges") {
            ds.connection.use { c ->
                c.autoCommit = false
                c.createStatement().execute("CREATE TEMP TABLE migrate_edge (src text, type text, dst text) ON COMMIT DROP")
                c.prepareStatement("INSERT INTO migrate_edge VALUES (?, ?, ?)").use { ps ->
                    edges.forEach { ps.setString(1, it.from); ps.setString(2, it.type); ps.setString(3, it.to); ps.addBatch() }
                    ps.executeBatch()
                }
                c.createStatement().executeUpdate(
                    "INSERT INTO drivine_edge (src, dst, type, props) SELECT a.nid, b.nid, e.type, '{}' FROM migrate_edge e " +
                        "JOIN drivine_node a ON a.props->>'$MARK' = e.src JOIN drivine_node b ON b.props->>'$MARK' = e.dst",
                )
                c.commit()
            }
        }
        sql(ds, "UPDATE drivine_node SET props = props - '$MARK' WHERE props ? '$MARK'")

        val expected = nodes.groupingBy { it.labels.sorted().joinToString(",") }.eachCount().toSortedMap() to
            edges.groupingBy { it.type }.eachCount().toSortedMap()
        val actual = counts(ds)
        println("Nodes: ${actual.first}\nEdges: ${actual.second}")
        assertEquals(expected.first, actual.first, "node counts by labels")
        assertEquals(expected.second, actual.second, "edge counts by type")
    }

    /** The source's app nodes and the edges between them, and the types of any edges from them to other nodes. */
    private data class Source(val nodes: List<Node>, val edges: List<Edge>, val crossing: List<String>)

    private fun read(): Source =
        GraphDatabase.driver(env("MIGRATE_NEO4J_URI"), AuthTokens.basic(env("MIGRATE_NEO4J_USERNAME"), env("MIGRATE_NEO4J_PASSWORD"))).use { driver ->
            driver.session(SessionConfig.builder().withDefaultAccessMode(AccessMode.READ).build()).use { session ->
                session.executeRead { tx ->
                    val nodes = tx.run(
                        "MATCH (n) WHERE ${appNode("n")} RETURN elementId(n) AS id, labels(n) AS labels, properties(n) AS props",
                    ).list { Node(it["id"].asString(), it["labels"].asList { l -> l.asString() }, it["props"].asMap()) }
                    val edges = tx.run(
                        "MATCH (a)-[r]->(b) WHERE ${appNode("a")} AND ${appNode("b")} " +
                            "RETURN elementId(a) AS from, type(r) AS type, elementId(b) AS to, properties(r) AS props",
                    ).list { Edge(it["from"].asString(), it["type"].asString(), it["to"].asString(), it["props"].asMap()) }
                    val crossing = tx.run(
                        "MATCH (a)-[r]->(b) WHERE (${appNode("a")}) <> (${appNode("b")}) RETURN DISTINCT type(r) AS type",
                    ).list { it["type"].asString() }
                    Source(nodes, edges, crossing)
                }
            }
        }

    private fun counts(ds: PGSimpleDataSource) = ds.connection.use { c ->
        fun query(q: String) = c.createStatement().executeQuery(q).use { rs ->
            buildMap { while (rs.next()) put(rs.getString(1), rs.getInt(2)) }.toSortedMap()
        }
        query(
            "SELECT (SELECT string_agg(l, ',' ORDER BY l) FROM jsonb_array_elements_text(labels) l), count(*) FROM drivine_node WHERE ${pgAppNode()} GROUP BY 1",
        ) to query(
            "SELECT e.type, count(*) FROM drivine_edge e JOIN drivine_node a ON a.nid = e.src JOIN drivine_node b ON b.nid = e.dst " +
                "WHERE ${pgAppNode("a.")} AND ${pgAppNode("b.")} GROUP BY 1",
        )
    }

    private fun sql(ds: PGSimpleDataSource, statement: String) = ds.connection.use { it.createStatement().executeUpdate(statement) }

    private inline fun <T> timed(what: String, block: () -> T): T {
        val t0 = System.nanoTime()
        return block().also { println("Wrote $what in ${(System.nanoTime() - t0) / 1_000_000} ms") }
    }

    companion object {
        /** Whether [v] is an app node in Neo4j: not content, not Drivine's own schema record. */
        fun appNode(v: String) = "coalesce($v.managedBy, '') <> 'embabel' AND NOT $v:_DrivineSchema"

        /** The same on Postgres, for a row of drivine_node aliased by [alias] ("a." or none). */
        fun pgAppNode(alias: String = "") =
            "coalesce(${alias}props->>'managedBy', '') <> 'embabel' AND NOT ${alias}labels @> '[\"_DrivineSchema\"]'"

        /** The app nodes Guide makes on its first start: the bot, the anonymous user and the system personas. */
        const val MADE_ON_START = "props->>'id' = 'bot:jesse' OR labels @> '[\"Anonymous\"]' " +
            "OR (labels @> '[\"Persona\"]' AND props->>'isSystem' = 'true') " +
            "OR (labels @> '[\"GuideUser\"]' AND EXISTS (SELECT 1 FROM drivine_edge e JOIN drivine_node w ON w.nid = e.dst " +
            "WHERE e.src = drivine_node.nid AND e.type = 'IS_WEB_USER' AND w.labels @> '[\"Anonymous\"]'))"

        /** Each node's Neo4j element id while edges are made, removed after. */
        const val MARK = "_migratedFrom"

        const val BATCH = 200
    }
}
