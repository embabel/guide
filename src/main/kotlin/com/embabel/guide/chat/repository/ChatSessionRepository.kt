package com.embabel.guide.chat.repository

import com.embabel.guide.chat.model.ChatSession
import com.embabel.guide.chat.model.MessageWithVersion
import java.util.Optional

/**
 * Repository interface for ChatSession operations.
 */
interface ChatSessionRepository {

    /**
     * Find a session by its ID, returning the full session view.
     */
    fun findBySessionId(sessionId: String): Optional<ChatSession>

    /**
     * Find all sessions owned by a user.
     */
    fun findByOwnerId(ownerId: String): List<ChatSession>

    /**
     * Create a new session with an initial message.
     *
     * @param sessionId the session ID (should be UUIDv7)
     * @param ownerId the owning user's ID
     * @param title optional session title
     * @param message the initial message text
     * @param role the message role ("user", "assistant", or "tool")
     * @param authorId optional author ID for the message (null for system messages)
     * @return the created session
     */
    fun createWithMessage(
        sessionId: String,
        ownerId: String,
        title: String?,
        message: String,
        role: String,
        authorId: String? = null
    ): ChatSession

    /**
     * Add a message to an existing session.
     *
     * @param sessionId the session ID
     * @param message the message to add
     * @return the updated session
     */
    fun addMessage(sessionId: String, message: MessageWithVersion): ChatSession

    /**
     * Delete all sessions (for testing).
     */
    fun deleteAll()
}
