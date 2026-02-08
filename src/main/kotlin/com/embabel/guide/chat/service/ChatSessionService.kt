package com.embabel.guide.chat.service

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
        const val WELCOME_PROMPT_TEMPLATE = "User %s has created a new account. Could you please greet and welcome them"
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
     * Sends a prompt to the AI asking it to greet and welcome the user.
     * The prompt itself is NOT stored in the session - only the AI's response.
     * The session is owned by the user, but the welcome message has no author (system-generated).
     *
     * @param ownerId the user who owns the session
     * @param displayName the user's display name for the personalized greeting
     */
    suspend fun createWelcomeSession(
        ownerId: String,
        displayName: String
    ): StoredSession = withContext(Dispatchers.IO) {
        // Generate sessionId upfront so we can pass it to the RAG adapter
        val sessionId = UUIDv7.generateString()
        val prompt = WELCOME_PROMPT_TEMPLATE.format(displayName)
        val welcomeMessage = ragAdapter.sendMessage(
            threadId = sessionId,
            message = prompt,
            fromUserId = ownerId
        )

        val owner = guideUserRepository.findById(ownerId).orElseThrow {
            IllegalArgumentException("Owner not found: $ownerId")
        }

        val messageData = MessageData(
            messageId = UUIDv7.generateString(),
            role = Role.ASSISTANT,
            content = welcomeMessage,
            createdAt = Instant.now()
        )

        val title = "Welcome"
        val session = chatSessionRepository.createSessionWithMessage(
            sessionId = sessionId,
            owner = owner.guideUserData(),
            title = title,
            messageData = messageData,
            messageAuthor = null  // System message - no author
        )

        // Publish event so UI receives the welcome message with title
        val persistedMessage = session.messages.last().toMessage()
        eventPublisher.publishEvent(
            MessageEvent.persisted(
                conversationId = sessionId,
                message = persistedMessage,
                fromUserId = null,  // System message
                toUserId = ownerId,
                title = title
            )
        )

        session
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
     * Create a new session from user message content.
     * Generates a title from the content using AI.
     *
     * @param ownerId the user who owns the session
     * @param content the initial message content
     * @return the created session
     */
    suspend fun createSessionFromContent(
        ownerId: String,
        content: String
    ): StoredSession = withContext(Dispatchers.IO) {
        val title = ragAdapter.generateTitle(content, ownerId)
        createSession(
            ownerId = ownerId,
            title = title,
            message = content,
            role = Role.USER,
            authorId = ownerId
        )
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
     * If the session doesn't exist, generates a title from the message content.
     *
     * Note: This method only creates the session metadata (title, owner).
     * Message persistence is handled by the chatbot via STORED conversations.
     *
     * @param sessionId the session ID (client-provided)
     * @param ownerId the user who owns the session
     * @param messageForTitle the message text (used only for title generation if new session)
     * @return SessionResult containing the session and whether it was created
     */
    suspend fun getOrCreateSession(
        sessionId: String,
        ownerId: String,
        messageForTitle: String
    ): SessionResult = withContext(Dispatchers.IO) {
        val existing = chatSessionRepository.findBySessionId(sessionId)
        if (existing.isPresent) {
            SessionResult(existing.get(), created = false)
        } else {
            // Session doesn't exist - create with generated title
            val title = ragAdapter.generateTitle(messageForTitle, ownerId)
            val owner = guideUserRepository.findById(ownerId).orElseThrow {
                IllegalArgumentException("Owner not found: $ownerId")
            }

            val session = chatSessionRepository.createSession(
                sessionId = sessionId,
                owner = owner.guideUserData(),
                title = title
            )
            SessionResult(session, created = true)
        }
    }
}
