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
    override fun findByOwnerId(ownerId: String): List<ThreadTimeline> {
        return graphObjectManager.loadAll<ThreadTimeline> {
            where {
                owner.core.id eq ownerId
            }
        }
    }

    @Transactional
    override fun createWithMessage(
        threadId: String,
        ownerId: String,
        title: String?,
        message: String,
        role: String,
        authorId: String?
    ): ThreadTimeline {
        val now = Instant.now()

        val owner = guideUserRepository.findById(ownerId).orElseThrow {
            IllegalArgumentException("Owner not found: $ownerId")
        }

        val author = if (authorId != null) {
            guideUserRepository.findById(authorId).orElseThrow {
                IllegalArgumentException("Author not found: $authorId")
            }
        } else {
            null
        }

        val messageId = UUIDv7.generateString()
        val versionId = UUIDv7.generateString()

        val timeline = ThreadTimeline(
            thread = ThreadData(
                threadId = threadId,
                title = title,
                createdAt = now
            ),
            owner = owner,
            _messages = listOf(
                MessageWithVersion(
                    message = MessageData(
                        messageId = messageId,
                        threadId = threadId,
                        role = role,
                        createdAt = now
                    ),
                    current = MessageVersionData(
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