/*
 * Copyright 2024-2025 Embabel Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.guide.chat.repository

import com.embabel.guide.Neo4jPropertiesInitializer
import com.embabel.guide.chat.service.ThreadService
import com.embabel.guide.domain.GuideUserData
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.domain.WebUserData
import com.embabel.guide.util.UUIDv7
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Test for ThreadRepositoryImpl using the type-safe DSL.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
@ImportAutoConfiguration(exclude = [McpClientAutoConfiguration::class])
@Transactional
class ThreadRepositoryImplTest {

    @Autowired
    private lateinit var threadRepository: ThreadRepositoryImpl

    @Autowired
    private lateinit var guideUserRepository: GuideUserRepository

    private lateinit var testUser: com.embabel.guide.domain.GuideUser

    @BeforeEach
    fun setUp() {
        // Create a test user for thread ownership
        val guideUserData = GuideUserData(
            UUID.randomUUID().toString(),
            null,
            null
        )
        val webUserData = WebUserData(
            "thread-test-${UUID.randomUUID()}",
            "Thread Test User",
            "threadtestuser-${UUID.randomUUID()}",
            "threadtest@example.com",
            "hashedpassword",
            null
        )
        testUser = guideUserRepository.createWithWebUser(guideUserData, webUserData)
    }

    @Test
    fun `test create thread with message and author`() {
        // Given: A thread ID and message
        val threadId = UUIDv7.generateString()
        val message = "Hello, this is a test message"

        // When: We create a thread with the message and author
        val created = threadRepository.createWithMessage(
            threadId = threadId,
            ownerId = testUser.core.id,
            title = "Test Thread",
            message = message,
            role = ThreadService.ROLE_USER,
            authorId = testUser.core.id
        )

        // Then: The thread is created with the correct data
        assertNotNull(created)
        assertEquals(threadId, created.thread.threadId)
        assertEquals("Test Thread", created.thread.title)
        assertNotNull(created.thread.createdAt)

        // And: The thread has the correct owner
        assertEquals(testUser.core.id, created.owner.core.id)

        // And: The thread has one message
        assertEquals(1, created.messages.size)
        val msg = created.messages.first()
        assertEquals(ThreadService.ROLE_USER, msg.message.role)
        assertEquals(message, msg.current.text)
        assertEquals(testUser.core.id, msg.authoredBy?.core?.id)
    }

    @Test
    fun `test create thread with system message (no author)`() {
        // Given: A thread ID and assistant message (system generated)
        val threadId = UUIDv7.generateString()
        val message = "Welcome! How can I help you?"

        // When: We create a thread with an assistant message and no author
        val created = threadRepository.createWithMessage(
            threadId = threadId,
            ownerId = testUser.core.id,
            title = "Welcome",
            message = message,
            role = ThreadService.ROLE_ASSISTANT,
            authorId = null  // System message
        )

        // Then: The thread is owned by the user
        assertEquals(testUser.core.id, created.owner.core.id)

        // And: The message has the assistant role but no author
        assertEquals(1, created.messages.size)
        assertEquals(ThreadService.ROLE_ASSISTANT, created.messages.first().message.role)
        assertEquals(message, created.messages.first().current.text)
        assertNull(created.messages.first().authoredBy)
    }

    @Test
    fun `test find thread by ID`() {
        // Given: We create a thread
        val threadId = UUIDv7.generateString()
        threadRepository.createWithMessage(
            threadId = threadId,
            ownerId = testUser.core.id,
            title = "Findable Thread",
            message = "Test message",
            role = ThreadService.ROLE_USER,
            authorId = testUser.core.id
        )

        // When: We find by thread ID
        val found = threadRepository.findByThreadId(threadId)

        // Then: The thread is found with correct owner
        assertTrue(found.isPresent)
        assertEquals(threadId, found.get().thread.threadId)
        assertEquals("Findable Thread", found.get().thread.title)
        assertEquals(testUser.core.id, found.get().owner.core.id)
        assertEquals(1, found.get().messages.size)
    }

    @Test
    fun `test findByThreadId returns empty when not found`() {
        // When: We search for a non-existent thread
        val found = threadRepository.findByThreadId("nonexistent-thread-id")

        // Then: An empty Optional is returned
        assertFalse(found.isPresent)
    }

    @Test
    fun `test find threads by owner ID`() {
        // Given: We create multiple threads for the test user
        val thread1Id = UUIDv7.generateString()
        val thread2Id = UUIDv7.generateString()

        threadRepository.createWithMessage(
            threadId = thread1Id,
            ownerId = testUser.core.id,
            title = "User Thread 1",
            message = "First thread message",
            role = ThreadService.ROLE_USER,
            authorId = testUser.core.id
        )

        threadRepository.createWithMessage(
            threadId = thread2Id,
            ownerId = testUser.core.id,
            title = "User Thread 2",
            message = "Second thread message",
            role = ThreadService.ROLE_USER,
            authorId = testUser.core.id
        )

        // When: We find threads by owner ID
        val threads = threadRepository.findByOwnerId(testUser.core.id)

        // Then: Both threads are found
        assertTrue(threads.size >= 2)
        assertTrue(threads.any { it.thread.threadId == thread1Id })
        assertTrue(threads.any { it.thread.threadId == thread2Id })
    }

    @Test
    fun `test findByOwnerId returns empty list when user has no threads`() {
        // Given: A user with no threads
        val anotherUser = guideUserRepository.createWithWebUser(
            GuideUserData(UUID.randomUUID().toString(), null, null),
            WebUserData(
                "no-threads-${UUID.randomUUID()}",
                "No Threads User",
                "nothreadsuser-${UUID.randomUUID()}",
                "nothreads@example.com",
                "hash",
                null
            )
        )

        // When: We find threads for this user
        val threads = threadRepository.findByOwnerId(anotherUser.core.id)

        // Then: Empty list is returned
        assertTrue(threads.isEmpty())
    }

    @Test
    fun `test thread has correct timestamps`() {
        // Given: We create a thread
        val threadId = UUIDv7.generateString()
        val beforeCreate = java.time.Instant.now()

        val created = threadRepository.createWithMessage(
            threadId = threadId,
            ownerId = testUser.core.id,
            title = null,
            message = "Timestamp test",
            role = ThreadService.ROLE_USER,
            authorId = testUser.core.id
        )

        val afterCreate = java.time.Instant.now()

        // Then: All timestamps are within the expected range
        assertNotNull(created.thread.createdAt)
        assertTrue(created.thread.createdAt!! >= beforeCreate.minusMillis(100))
        assertTrue(created.thread.createdAt!! <= afterCreate.plusMillis(100))

        val msg = created.messages.first()
        assertNotNull(msg.message.createdAt)
        assertNotNull(msg.current.createdAt)
    }

    @Test
    fun `test thread without title`() {
        // Given: We create a thread without a title
        val threadId = UUIDv7.generateString()

        // When: We create the thread with null title
        val created = threadRepository.createWithMessage(
            threadId = threadId,
            ownerId = testUser.core.id,
            title = null,
            message = "No title thread",
            role = ThreadService.ROLE_USER,
            authorId = testUser.core.id
        )

        // Then: The thread is created with null title
        assertNull(created.thread.title)
    }

    @Test
    fun `test deleteAll removes all threads`() {
        // Given: We create a thread
        val threadId = UUIDv7.generateString()
        threadRepository.createWithMessage(
            threadId = threadId,
            ownerId = testUser.core.id,
            title = "Delete Test",
            message = "Will be deleted",
            role = ThreadService.ROLE_USER,
            authorId = testUser.core.id
        )

        // When: We delete all threads
        threadRepository.deleteAll()

        // Then: The thread is no longer found
        val found = threadRepository.findByThreadId(threadId)
        assertFalse(found.isPresent)
    }

    @Test
    fun `test message version has correct editor role`() {
        // Given: We create threads with different roles
        val userThreadId = UUIDv7.generateString()
        val assistantThreadId = UUIDv7.generateString()

        val userThread = threadRepository.createWithMessage(
            threadId = userThreadId,
            ownerId = testUser.core.id,
            title = null,
            message = "User message",
            role = ThreadService.ROLE_USER,
            authorId = testUser.core.id
        )

        val assistantThread = threadRepository.createWithMessage(
            threadId = assistantThreadId,
            ownerId = testUser.core.id,
            title = null,
            message = "Assistant message",
            role = ThreadService.ROLE_ASSISTANT,
            authorId = null
        )

        // Then: The editor role matches the message role
        assertEquals(ThreadService.ROLE_USER, userThread.messages.first().current.editorRole)
        assertEquals(ThreadService.ROLE_ASSISTANT, assistantThread.messages.first().current.editorRole)
    }

    @Test
    fun `test createWithMessage throws when owner not found`() {
        // When/Then: Creating a thread with non-existent owner throws
        assertThrows(IllegalArgumentException::class.java) {
            threadRepository.createWithMessage(
                threadId = UUIDv7.generateString(),
                ownerId = "nonexistent-user-id",
                title = null,
                message = "Should fail",
                role = ThreadService.ROLE_USER,
                authorId = null
            )
        }
    }
}