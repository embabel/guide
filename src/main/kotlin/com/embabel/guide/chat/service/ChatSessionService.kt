package com.embabel.guide.chat.service

import com.embabel.chat.ChatTrigger
import com.embabel.chat.Role
import com.embabel.chat.event.MessageEvent
import com.embabel.chat.store.model.MessageData
import com.embabel.chat.store.model.StoredSession
import com.embabel.chat.store.repository.ChatSessionRepository
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.util.UUIDv7
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Optional

/**
 * Service for managing chat session metadata (titles, ownership, listing).
 * Message persistence is handled by the chatbot via STORED conversations.
 */
@Service
class ChatSessionService(
    private val chatSessionRepository: ChatSessionRepository,
    private val ragAdapter: RagServiceAdapter,
    private val guideUserRepository: GuideUserRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    companion object {
        const val DEFAULT_WELCOME_MESSAGE = "Welcome! How can I help you today?"
        const val WELCOME_PROMPT_TEMPLATE = "User %s has created a new account. Please briefly greet and welcome them"
    }

    /**
     * Find a session by its ID.
     */
    fun findBySessionId(sessionId: String): Optional<StoredSession> {
        return chatSessionRepository.findBySessionId(sessionId)
    }

    /**
     * Find all sessions owned by a user.
     */
    fun findByOwnerId(ownerId: String): List<StoredSession> {
        return chatSessionRepository.listSessionsForUser(ownerId)
    }

    /**
     * Find all sessions owned by a user, sorted by most recent activity.
     * Sessions with the most recent messages appear first.
     */
    fun findByOwnerIdByRecentActivity(ownerId: String): List<StoredSession> {
        return chatSessionRepository.listSessionsForUser(ownerId)
            .sortedByDescending { it.messages.lastOrNull()?.messageId ?: "" }
    }

    /**
     * Create a new session with an initial message.
     *
     * @param ownerId the user who owns the session
     * @param title optional session title
     * @param message the initial message text
     * @param role the message role
     * @param authorId optional author of the message (null for system messages)
     */
    fun createSession(
        ownerId: String,
        title: String? = null,
        message: String,
        role: Role,
        authorId: String? = null
    ): StoredSession {
        val sessionId = UUIDv7.generateString()
        val owner = guideUserRepository.findById(ownerId).orElseThrow {
            IllegalArgumentException("Owner not found: $ownerId")
        }

        val messageData = MessageData(
            messageId = UUIDv7.generateString(),
            role = role,
            content = message,
            createdAt = Instant.now()
        )

        // Look up the author if provided
        val messageAuthor = authorId?.let { id ->
            guideUserRepository.findById(id).orElse(null)?.guideUserData()
        }

        return chatSessionRepository.createSessionWithMessage(
            sessionId = sessionId,
            owner = owner.guideUserData(),
            title = title,
            messageData = messageData,
            messageAuthor = messageAuthor
        )
    }

    /**
     * Create a welcome session for a new user with an AI-generated greeting.
     *
     * Creates the DB session first (with title "Welcome") so that when the chatbot
     * processes the message via StoredConversation, the ADDED event includes the title
     * for delivery to the client.
     *
     * Message persistence and event publishing are handled by the chatbot's
     * StoredConversation — no manual event publishing needed here.
     *
     * @param ownerId the user who owns the session
     * @param displayName the user's display name for the personalized greeting
     */
    suspend fun createWelcomeSession(
        ownerId: String,
        displayName: String
    ): StoredSession = withContext(Dispatchers.IO) {
        val sessionId = UUIDv7.generateString()
        val owner = guideUserRepository.findById(ownerId).orElseThrow {
            IllegalArgumentException("Owner not found: $ownerId")
        }

        // Create DB session FIRST so StoredConversation finds it with the title
        val title = "Welcome"
        chatSessionRepository.createSession(
            sessionId = sessionId,
            owner = owner.guideUserData(),
            title = title
        )

        // Send trigger — prompt never enters conversation, only the response is stored
        val trigger = ChatTrigger(
            prompt = WELCOME_PROMPT_TEMPLATE.format(displayName),
            onBehalfOf = listOf(owner),
        )
        ragAdapter.sendTrigger(threadId = sessionId, trigger = trigger)

        // Return the session with persisted messages
        chatSessionRepository.findBySessionId(sessionId).orElseThrow {
            IllegalStateException("Welcome session $sessionId not found after creation")
        }
    }

    /**
     * Create a welcome session with a static message (for testing or fallback).
     */
    fun createWelcomeSessionWithMessage(
        ownerId: String,
        welcomeMessage: String = DEFAULT_WELCOME_MESSAGE
    ): StoredSession {
        val title = "Welcome"
        val session = createSession(
            ownerId = ownerId,
            title = title,
            message = welcomeMessage,
            role = Role.ASSISTANT,
            authorId = null
        )

        // Publish event so UI receives the welcome message with title
        val persistedMessage = session.messages.last().toMessage()
        eventPublisher.publishEvent(
            MessageEvent.persisted(
                conversationId = session.session.sessionId,
                message = persistedMessage,
                fromUserId = null,  // System message
                toUserId = ownerId,
                title = title
            )
        )

        return session
    }

    /**
     * Result of getOrCreateSession - contains the session and whether it was newly created.
     */
    data class SessionResult(
        val session: StoredSession,
        val created: Boolean
    )

    /**
     * Get an existing session or create a new one.
     * Title is generated automatically by [StoredConversation] on the first message
     * via the [TitleGenerator] bean.
     *
     * Note: This method only creates the session metadata (owner).
     * Message persistence is handled by the chatbot via STORED conversations.
     *
     * @param sessionId the session ID (client-provided)
     * @param ownerId the user who owns the session
     * @return SessionResult containing the session and whether it was created
     */
    suspend fun getOrCreateSession(
        sessionId: String,
        ownerId: String,
    ): SessionResult = withContext(Dispatchers.IO) {
        val existing = chatSessionRepository.findBySessionId(sessionId)
        if (existing.isPresent) {
            SessionResult(existing.get(), created = false)
        } else {
            val owner = guideUserRepository.findById(ownerId).orElseThrow {
                IllegalArgumentException("Owner not found: $ownerId")
            }

            val session = chatSessionRepository.createSession(
                sessionId = sessionId,
                owner = owner.guideUserData(),
                title = null
            )
            SessionResult(session, created = true)
        }
    }
}
