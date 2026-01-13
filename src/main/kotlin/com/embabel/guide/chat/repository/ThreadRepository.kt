package com.embabel.guide.chat.repository

import com.embabel.guide.chat.model.ThreadTimeline
import java.util.Optional

/**
 * Repository interface for Thread operations.
 */
interface ThreadRepository {

    /**
     * Find a thread by its ID, returning the full timeline view.
     */
    fun findByThreadId(threadId: String): Optional<ThreadTimeline>

    /**
     * Find all threads owned by a user.
     */
    fun findByOwnerId(ownerId: String): List<ThreadTimeline>

    /**
     * Create a new thread with an initial message.
     *
     * @param threadId the thread ID (should be UUIDv7)
     * @param ownerId the owning user's ID
     * @param title optional thread title
     * @param message the initial message text
     * @param role the message role ("user", "assistant", or "tool")
     * @param authorId optional author ID for the message (null for system messages)
     * @return the created thread timeline
     */
    fun createWithMessage(
        threadId: String,
        ownerId: String,
        title: String?,
        message: String,
        role: String,
        authorId: String? = null
    ): ThreadTimeline

    /**
     * Delete all threads (for testing).
     */
    fun deleteAll()
}
