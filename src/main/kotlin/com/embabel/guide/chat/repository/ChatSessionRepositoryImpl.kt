package com.embabel.guide.chat.repository

import com.embabel.guide.chat.model.*
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.util.UUIDv7
import org.drivine.manager.GraphObjectManager
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Optional

@Repository
class ChatSessionRepositoryImpl(
    @param:Qualifier("neoGraphObjectManager") private val graphObjectManager: GraphObjectManager,
    private val guideUserRepository: GuideUserRepository
) : ChatSessionRepository {

    @Transactional(readOnly = true)
    override fun findBySessionId(sessionId: String): Optional<ChatSession> {
        val results = graphObjectManager.loadAll<ChatSession> {
            where {
                session.sessionId eq sessionId
            }
            orderBy {
                messages.message.messageId.asc()
            }
        }
        return Optional.ofNullable(results.firstOrNull())
    }

    @Transactional(readOnly = true)
    override fun findByOwnerId(ownerId: String): List<ChatSession> {
        return graphObjectManager.loadAll<ChatSession> {
            where {
                owner.core.id eq ownerId
            }
            orderBy {
                messages.message.messageId.asc()
            }
        }
    }

    @Transactional
    override fun createWithMessage(
        sessionId: String,
        ownerId: String,
        title: String?,
        message: String,
        role: String,
        authorId: String?
    ): ChatSession {
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

        val chatSession = ChatSession(
            session = ChatSessionData(
                sessionId = sessionId,
                title = title,
                createdAt = now
            ),
            owner = owner,
            messages = listOf(
                MessageWithVersion(
                    message = MessageData(
                        messageId = messageId,
                        sessionId = sessionId,
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

        return graphObjectManager.save(chatSession)
    }

    @Transactional
    override fun addMessage(sessionId: String, message: MessageWithVersion): ChatSession {
        val chatSession = findBySessionId(sessionId).orElseThrow {
            IllegalArgumentException("Session not found: $sessionId")
        }
        val updatedSession = chatSession.withMessage(message)
        return graphObjectManager.save(updatedSession)
    }

    @Transactional
    override fun deleteAll() {
        graphObjectManager.deleteAll<ChatSession> { }
    }
}
