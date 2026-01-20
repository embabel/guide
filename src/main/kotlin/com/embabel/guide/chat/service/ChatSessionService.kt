package com.embabel.guide.chat.service

import com.embabel.guide.chat.model.*
import com.embabel.guide.chat.repository.ChatSessionRepository
import com.embabel.guide.domain.GuideUserService
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
    private val guideUserService: GuideUserService
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
    fun findBySessionId(sessionId: String): Optional<ChatSession> {
        return chatSessionRepository.findBySessionId(sessionId)
    }

    /**
     * Find all sessions owned by a user.
     */
    fun findByOwnerId(ownerId: String): List<ChatSession> {
        return chatSessionRepository.findByOwnerId(ownerId)
    }

    /**
     * Find all sessions owned by a user, sorted by most recent activity.
     * Sessions with the most recent messages appear first.
     */
    fun findByOwnerIdByRecentActivity(ownerId: String): List<ChatSession> {
        return chatSessionRepository.findByOwnerId(ownerId)
            .sortedByDescending { it.messages.lastOrNull()?.message?.messageId ?: "" }
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
    ): ChatSession {
        val sessionId = UUIDv7.generateString()
        return chatSessionRepository.createWithMessage(
            sessionId = sessionId,
            ownerId = ownerId,
            title = title,
            message = message,
            role = role,
            authorId = authorId
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
    ): ChatSession = withContext(Dispatchers.IO) {
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

        chatSessionRepository.createWithMessage(
            sessionId = sessionId,
            ownerId = ownerId,
            title = "Welcome",
            message = welcomeMessage,
            role = ROLE_ASSISTANT,
            authorId = null  // System message - no author
        )
    }

    /**
     * Create a welcome session with a static message (for testing or fallback).
     */
    fun createWelcomeSessionWithMessage(
        ownerId: String,
        welcomeMessage: String = DEFAULT_WELCOME_MESSAGE
    ): ChatSession {
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
    ): ChatSession = withContext(Dispatchers.IO) {
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
    ): MessageWithVersion {
        val now = Instant.now()

        val author = if (authorId != null) {
            guideUserService.findById(authorId).orElseThrow {
                IllegalArgumentException("Author not found: $authorId")
            }
        } else {
            null
        }

        val messageId = UUIDv7.generateString()
        val versionId = UUIDv7.generateString()

        val message = MessageWithVersion(
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
                text = text
            ),
            authoredBy = author
        )

        chatSessionRepository.addMessage(sessionId, message)
        return message
    }
}
