package com.embabel.guide.migration

import org.drivine.annotation.NodeFragment
import org.drivine.connection.DatabaseType
import org.drivine.connection.JdbcConnectionProvider
import org.drivine.mapper.SubtypeRegistry
import org.drivine.migration.MigrationReport.Status
import org.drivine.migration.StoreLayoutMigration
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter

/**
 * One-off: moves a Guide graph on Postgres from Drivine's store layout 1 (written by drivine4j 0.7.0 and before) to
 * layout 2, which drivine4j 0.8.0 and later read, and refuse a layout-1 graph without. See
 * https://github.com/liberation-data/drivine4j/blob/v0.9.0/docs/19-migrating-a-store-layout.md.
 *
 * It declares every `@NodeFragment` on the classpath under `com.embabel` (Guide's, the chat store's and the RAG
 * graph's), so their dates, moments and floats are rewritten as the kinds they are. A dry run by default: it writes
 * only with LAYOUT_MIGRATE=true. Nothing else may write to the graph while it runs: stop Guide first, and take a
 * backup (`pg_dump -t 'drivine_*'`). It holds two connections at once.
 *
 *     LAYOUT_POSTGRES_URL=jdbc:postgresql://localhost:5432/guide LAYOUT_POSTGRES_USERNAME=guide LAYOUT_POSTGRES_PASSWORD=… \
 *     [LAYOUT_MIGRATE=true] ./mvnw -q test -Dtest=StoreLayoutMigrationRunner -Dsurefire.failIfNoSpecifiedTests=false
 */
@EnabledIfEnvironmentVariable(named = "LAYOUT_POSTGRES_URL", matches = ".+")
class StoreLayoutMigrationRunner {

    private fun env(name: String) = requireNotNull(System.getenv(name)) { "$name is not set" }

    @Test
    fun migrate() {
        val ds = PGSimpleDataSource().apply {
            setURL(env("LAYOUT_POSTGRES_URL"))
            user = env("LAYOUT_POSTGRES_USERNAME")
            password = env("LAYOUT_POSTGRES_PASSWORD")
        }
        val fragments = nodeFragments()
        println("Declaring ${fragments.size} node fragments: ${fragments.map { it.simpleName }.sorted()}")
        val declarations = fragments.flatMap { StoreLayoutMigration.declarationsOf(it) }.distinct()
        declarations.forEach { println("  $it") }

        val dryRun = System.getenv("LAYOUT_MIGRATE") != "true"
        val migration = StoreLayoutMigration(JdbcConnectionProvider("layout", DatabaseType.POSTGRES, ds, SubtypeRegistry()))
        val report = migration.migrate(declarations, dryRun = dryRun)
        println("${if (dryRun) "Dry run" else "Migration"}: ${report.status} ${report.message ?: ""}")
        println("Tables: ${report.tables}")
        println("Properties: ${report.properties}")
        println("Undeclared candidates: ${report.candidates}")
        println("Collisions: ${report.collisions}")
        val ok = if (dryRun) setOf(Status.DRY_RUN, Status.CURRENT, Status.EMPTY) else setOf(Status.MIGRATED, Status.CURRENT, Status.EMPTY)
        assertTrue(report.status in ok, "${report.status}: ${report.message}")
    }

    private fun nodeFragments(): List<Class<*>> {
        // Interfaces and abstract classes carry @NodeFragment too, which the scanner skips unless told otherwise.
        val scanner = object : ClassPathScanningCandidateComponentProvider(false) {
            override fun isCandidateComponent(beanDefinition: AnnotatedBeanDefinition) = true
        }.apply { addIncludeFilter(AnnotationTypeFilter(NodeFragment::class.java)) }
        return scanner.findCandidateComponents("com.embabel").mapNotNull { def ->
            def.beanClassName?.let { Class.forName(it) }
        }.distinct()
    }
}
