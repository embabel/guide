package com.embabel.guide.chat.service

import com.embabel.chat.store.model.MessageData
import com.embabel.chat.store.model.StoredMessage
import com.embabel.chat.store.model.StoredSession
import com.embabel.chat.store.repository.ChatSessionRepository
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.util.UUIDv7
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Optional

@Service
class ChatSessionService(
    private val chatSessionRepository: ChatSessionRepository,
    private val ragAdapter: RagServiceAdapter,
    private val guideUserRepository: GuideUserRepository
) {

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_TOOL = "tool"

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
        role: String,
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
            fromUserId = ownerId,
            priorMessages = emptyList(),  // No prior context for welcome session
            onEvent = { }  // No status updates needed for welcome message
        )

        val owner = guideUserRepository.findById(ownerId).orElseThrow {
            IllegalArgumentException("Owner not found: $ownerId")
        }

        val messageData = MessageData(
            messageId = UUIDv7.generateString(),
            role = ROLE_ASSISTANT,
            content = welcomeMessage,
            createdAt = Instant.now()
        )

        chatSessionRepository.createSessionWithMessage(
            sessionId = sessionId,
            owner = owner.guideUserData(),
            title = "Welcome",
            messageData = messageData,
            messageAuthor = null  // System message - no author
        )
    }

    /**
     * Create a welcome session with a static message (for testing or fallback).
     */
    fun createWelcomeSessionWithMessage(
        ownerId: String,
        welcomeMessage: String = DEFAULT_WELCOME_MESSAGE
    ): StoredSession {
        return createSession(
            ownerId = ownerId,
            title = "Welcome",
            message = welcomeMessage,
            role = ROLE_ASSISTANT,
            authorId = null
        )
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
            role = ROLE_USER,
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
     * Get an existing session or create a new one with the given message.
     * If the session doesn't exist, generates a title from the message content.
     *
     * @param sessionId the session ID (client-provided)
     * @param ownerId the user who owns the session
     * @param message the message text
     * @param authorId the author of the message
     * @return SessionResult containing the session and whether it was created
     */
    suspend fun getOrCreateSessionWithMessage(
        sessionId: String,
        ownerId: String,
        message: String,
        authorId: String
    ): SessionResult = withContext(Dispatchers.IO) {
        val existing = chatSessionRepository.findBySessionId(sessionId)
        if (existing.isPresent) {
            // Session exists - just add the message
            addMessage(sessionId, message, ROLE_USER, authorId)
            SessionResult(existing.get(), created = false)
        } else {
            // Session doesn't exist - create with generated title
            val title = ragAdapter.generateTitle(message, ownerId)
            val owner = guideUserRepository.findById(ownerId).orElseThrow {
                IllegalArgumentException("Owner not found: $ownerId")
            }

            val messageData = MessageData(
                messageId = UUIDv7.generateString(),
                role = ROLE_USER,
                content = message,
                createdAt = Instant.now()
            )

            val messageAuthor = guideUserRepository.findById(authorId).orElse(null)?.guideUserData()

            val session = chatSessionRepository.createSessionWithMessage(
                sessionId = sessionId,
                owner = owner.guideUserData(),
                title = title,
                messageData = messageData,
                messageAuthor = messageAuthor
            )
            SessionResult(session, created = true)
        }
    }

    /**
     * Add a message to an existing session.
     *
     * @param sessionId the session to add the message to
     * @param text the message text
     * @param role the message role (user, assistant, tool)
     * @param authorId optional author ID (null for system messages)
     * @return the created message
     */
    fun addMessage(
        sessionId: String,
        text: String,
        role: String,
        authorId: String? = null
    ): StoredMessage {
        val messageData = MessageData(
            messageId = UUIDv7.generateString(),
            role = role,
            content = text,
            createdAt = Instant.now()
        )

        // Look up the author if provided
        val author = authorId?.let { id ->
            guideUserRepository.findById(id).orElse(null)?.guideUserData()
        }

        val updatedSession = chatSessionRepository.addMessage(sessionId, messageData, author)
        // Return the last message (the one we just added)
        return updatedSession.messages.last()
    }
}
