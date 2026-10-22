package com.embabel.guide.migration

import com.embabel.agent.rag.graph.model.ChunkExpandView
import com.embabel.agent.rag.graph.model.ContentTreeView
import com.embabel.agent.rag.graph.model.EntityInContextView
import com.embabel.agent.rag.graph.model.NextChunkLinkView
import com.embabel.agent.rag.graph.model.ParentLinkView
import com.embabel.agent.rag.graph.model.TypedContentTreeView
import com.embabel.agent.rag.graph.model.ZoomOutView
import com.embabel.chat.store.model.AttributedMessage
import com.embabel.chat.store.model.DeletableMessage
import com.embabel.chat.store.model.DeletableSession
import com.embabel.chat.store.model.NewMessageInSession
import com.embabel.chat.store.model.SessionParticipants
import com.embabel.chat.store.model.SessionSummary
import com.embabel.chat.store.model.SimpleStoredMessage
import com.embabel.chat.store.model.StoredSession
import com.embabel.guide.domain.AnonymousGuideUser
import com.embabel.guide.domain.FeedbackView
import com.embabel.guide.domain.GuideUser
import com.embabel.guide.domain.GuideWebUser
import com.embabel.guide.domain.PersonaView
import org.drivine.manager.PersistenceManager
import org.drivine.migration.PathRelationshipReport
import org.drivine.migration.RelationshipDirectionRepair
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Reports, and on request repairs, what a view save before Drivine 0.1.0 left wrong in the store:
 * a relationship of an `INCOMING` field written from the root to the target, and a `@GraphPath`
 * field or a several-hop list written as direct relationships. In Guide's own model that is
 * [FeedbackView.user]: `GAVE` was stored from the feedback to the user, where it is read from the
 * user to the feedback.
 *
 * Off unless `guide.repair.mode` is set:
 * - `report` changes nothing. It logs every finding over Guide's views and those of the chat store
 *   and the RAG store.
 * - `repair` logs the same report, then turns round the relationships of each field named in
 *   `guide.repair.fields` (`View.field`, comma separated). An ambiguous finding is not repaired.
 *
 * Run it once against each database, `report` first, after backing the store up. The tools are
 * written in Cypher and have nothing to repair on Oracle or Postgres.
 */
@Component
@ConditionalOnProperty(name = ["guide.repair.mode"])
class RelationshipRepairRunner(
    @param:Qualifier("neo") private val persistenceManager: PersistenceManager,
    @param:Value("\${guide.repair.mode}") private val mode: String,
    @param:Value("\${guide.repair.fields:}") private val fields: List<String>,
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(RelationshipRepairRunner::class.java)

    override fun run(args: ApplicationArguments) {
        require(mode == "report" || mode == "repair") {
            "guide.repair.mode must be 'report' or 'repair', not '$mode'"
        }
        reportPaths()
        val repair = RelationshipDirectionRepair(persistenceManager)
        val findings = repair.report(*VIEWS)
        findings.forEach {
            logger.info(
                "[REPAIR] {}: {} {} wrong way, {} right way, {} either way, {} collisions{}",
                it.name, it.type, it.wrongWay, it.rightWay, it.eitherWay, it.collisions,
                it.ambiguity?.let { why -> ". Ambiguous: $why" } ?: "",
            )
        }
        repair.unexamined(*VIEWS).forEach { logger.warn("[REPAIR] Not examined: {}", it) }
        if (mode != "repair") return

        val unknown = fields - findings.map { it.name }.toSet()
        require(unknown.isEmpty()) { "guide.repair.fields names no INCOMING field of the views: $unknown" }
        findings.filter { it.name in fields }.forEach {
            val turned = repair.repair(it)
            logger.info("[REPAIR] {}: dealt with {} wrong-way {} relationships", it.name, turned, it.type)
        }
        repair.report(*VIEWS).filter { it.name in fields }.forEach {
            logger.info("[REPAIR] {}: {} wrong way after the repair", it.name, it.wrongWay)
        }
    }

    private fun reportPaths() {
        val paths = PathRelationshipReport(persistenceManager)
        paths.report(*VIEWS).forEach {
            logger.info(
                "[REPAIR] path {}.{}: {} direct {} relationships{}. Removal: {}",
                it.view.simpleName, it.field, it.direct, it.type,
                it.ambiguity?.let { why -> ". Ambiguous: $why" } ?: "",
                it.removalStatement ?: "none given",
            )
        }
        paths.reportSeveralHopLists(*VIEWS).forEach {
            logger.info(
                "[REPAIR] several-hop list {}.{}: {} direct {} relationships",
                it.view.simpleName, it.field, it.direct, it.type,
            )
        }
    }

    private val org.drivine.migration.DirectionFinding.name get() = "${view.simpleName}.$field"

    companion object {
        /** Every view saved to or read from Guide's graph: Guide's own, the chat store's and the RAG store's. */
        private val VIEWS: Array<Class<*>> = arrayOf(
            GuideUser::class.java,
            GuideWebUser::class.java,
            AnonymousGuideUser::class.java,
            FeedbackView::class.java,
            PersonaView::class.java,
            StoredSession::class.java,
            SessionSummary::class.java,
            SessionParticipants::class.java,
            DeletableSession::class.java,
            DeletableMessage::class.java,
            SimpleStoredMessage::class.java,
            AttributedMessage::class.java,
            NewMessageInSession::class.java,
            ChunkExpandView::class.java,
            ContentTreeView::class.java,
            TypedContentTreeView::class.java,
            EntityInContextView::class.java,
            NextChunkLinkView::class.java,
            ParentLinkView::class.java,
            ZoomOutView::class.java,
        )
    }
}
