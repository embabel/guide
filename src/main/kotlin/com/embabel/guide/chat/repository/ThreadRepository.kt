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
     * Find all threads for a user.
     * Ownership is determined by the first message's author.
     */
    fun findByUserId(userId: String): List<ThreadTimeline>

    /**
     * Create a new thread with an initial message.
     *
     * @param threadId the thread ID (should be UUIDv7)
     * @param userId the owning user's ID
     * @param title optional thread title
     * @param message the initial message text
     * @param role the message role ("user", "assistant", or "tool")
     * @return the created thread timeline
     */
    fun createWithMessage(
        threadId: String,
        userId: String,
        title: String?,
        message: String,
        role: String
    ): ThreadTimeline

    /**
     * Delete all threads (for testing).
     */
    fun deleteAll()
}
