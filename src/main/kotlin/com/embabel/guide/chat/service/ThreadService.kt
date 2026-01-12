package com.embabel.guide.chat.service

import com.embabel.guide.chat.model.ThreadTimeline
import com.embabel.guide.chat.repository.ThreadRepository
import com.embabel.guide.util.UUIDv7
import org.springframework.stereotype.Service
import java.util.Optional

@Service
class ThreadService(
    private val threadRepository: ThreadRepository
) {

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_TOOL = "tool"

        const val DEFAULT_WELCOME_MESSAGE = "Welcome! How can I help you today?"
    }

    /**
     * Find a thread by its ID.
     */
    fun findByThreadId(threadId: String): Optional<ThreadTimeline> {
        return threadRepository.findByThreadId(threadId)
    }

    /**
     * Find all threads for a user.
     */
    fun findByUserId(userId: String): List<ThreadTimeline> {
        return threadRepository.findByUserId(userId)
    }

    /**
     * Create a new thread with an initial message.
     */
    fun createThread(
        userId: String,
        title: String? = null,
        message: String,
        role: String
    ): ThreadTimeline {
        val threadId = UUIDv7.generateString()
        return threadRepository.createWithMessage(
            threadId = threadId,
            userId = userId,
            title = title,
            message = message,
            role = role
        )
    }

    /**
     * Create a welcome thread for a new user.
     * The welcome message is from the assistant.
     */
    fun createWelcomeThread(
        userId: String,
        welcomeMessage: String = DEFAULT_WELCOME_MESSAGE
    ): ThreadTimeline {
        return createThread(
            userId = userId,
            title = "Welcome",
            message = welcomeMessage,
            role = ROLE_ASSISTANT
        )
    }
}