package com.embabel.guide.chat.repository

import com.embabel.guide.chat.model.*
import com.embabel.guide.domain.GuideUser
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.util.UUIDv7
import org.drivine.manager.GraphObjectManager
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Optional

@Repository
class ThreadRepositoryImpl(
    @param:Qualifier("neoGraphObjectManager") private val graphObjectManager: GraphObjectManager,
    private val guideUserRepository: GuideUserRepository
) : ThreadRepository {

    @Transactional(readOnly = true)
    override fun findByThreadId(threadId: String): Optional<ThreadTimeline> {
        val results = graphObjectManager.loadAll<ThreadTimeline> {
            where {
                thread.threadId eq threadId
            }
        }
        return Optional.ofNullable(results.firstOrNull())
    }

    @Transactional(readOnly = true)
    override fun findByUserId(userId: String): List<ThreadTimeline> {
        return graphObjectManager.loadAll<ThreadTimeline> {
            where {
                turns.authoredBy.core.id eq userId
            }
        }
    }

    @Transactional
    override fun createWithMessage(
        threadId: String,
        userId: String,
        title: String?,
        message: String,
        role: String
    ): ThreadTimeline {
        val now = Instant.now()

        val author = guideUserRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User not found: $userId")
        }

        val turnId = UUIDv7.generateString()
        val versionId = UUIDv7.generateString()

        val timeline = ThreadTimeline(
            thread = ThreadCore(
                threadId = threadId,
                title = title,
                createdAt = now
            ),
            turns = listOf(
                TurnWithCurrentText(
                    turn = TurnCore(
                        turnId = turnId,
                        threadId = threadId,
                        role = role,
                        createdAt = now
                    ),
                    current = TurnVersionCore(
                        versionId = versionId,
                        createdAt = now,
                        editorRole = role,
                        reason = null,
                        text = message
                    ),
                    authoredBy = author
                )
            )
        )

        return graphObjectManager.save(timeline)
    }

    @Transactional
    override fun deleteAll() {
        graphObjectManager.deleteAll<ThreadTimeline> { }
    }
}