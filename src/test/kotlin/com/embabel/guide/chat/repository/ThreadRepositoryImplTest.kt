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
        // Create a test user for thread authorship
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
    fun `test create thread with message`() {
        // Given: A thread ID and message
        val threadId = UUIDv7.generateString()
        val message = "Hello, this is a test message"

        // When: We create a thread with the message
        val created = threadRepository.createWithMessage(
            threadId = threadId,
            userId = testUser.core.id,
            title = "Test Thread",
            message = message,
            role = ThreadService.ROLE_USER
        )

        // Then: The thread is created with the correct data
        assertNotNull(created)
        assertEquals(threadId, created.thread.threadId)
        assertEquals("Test Thread", created.thread.title)
        assertNotNull(created.thread.createdAt)

        // And: The thread has one turn with the message
        assertEquals(1, created.turns.size)
        val turn = created.turns.first()
        assertEquals(ThreadService.ROLE_USER, turn.turn.role)
        assertEquals(message, turn.current.text)
        assertEquals(testUser.core.id, turn.authoredBy?.core?.id)
    }

    @Test
    fun `test create thread with assistant message`() {
        // Given: A thread ID and assistant message
        val threadId = UUIDv7.generateString()
        val message = "Welcome! How can I help you?"

        // When: We create a thread with an assistant message
        val created = threadRepository.createWithMessage(
            threadId = threadId,
            userId = testUser.core.id,
            title = "Welcome",
            message = message,
            role = ThreadService.ROLE_ASSISTANT
        )

        // Then: The turn has the assistant role
        assertEquals(1, created.turns.size)
        assertEquals(ThreadService.ROLE_ASSISTANT, created.turns.first().turn.role)
        assertEquals(message, created.turns.first().current.text)
    }

    @Test
    fun `test find thread by ID`() {
        // Given: We create a thread
        val threadId = UUIDv7.generateString()
        threadRepository.createWithMessage(
            threadId = threadId,
            userId = testUser.core.id,
            title = "Findable Thread",
            message = "Test message",
            role = ThreadService.ROLE_USER
        )

        // When: We find by thread ID
        val found = threadRepository.findByThreadId(threadId)

        // Then: The thread is found
        assertTrue(found.isPresent)
        assertEquals(threadId, found.get().thread.threadId)
        assertEquals("Findable Thread", found.get().thread.title)
        assertEquals(1, found.get().turns.size)
    }

    @Test
    fun `test findByThreadId returns empty when not found`() {
        // When: We search for a non-existent thread
        val found = threadRepository.findByThreadId("nonexistent-thread-id")

        // Then: An empty Optional is returned
        assertFalse(found.isPresent)
    }

    @Test
    fun `test find threads by user ID`() {
        // Given: We create multiple threads for the test user
        val thread1Id = UUIDv7.generateString()
        val thread2Id = UUIDv7.generateString()

        threadRepository.createWithMessage(
            threadId = thread1Id,
            userId = testUser.core.id,
            title = "User Thread 1",
            message = "First thread message",
            role = ThreadService.ROLE_USER
        )

        threadRepository.createWithMessage(
            threadId = thread2Id,
            userId = testUser.core.id,
            title = "User Thread 2",
            message = "Second thread message",
            role = ThreadService.ROLE_USER
        )

        // When: We find threads by user ID
        val threads = threadRepository.findByUserId(testUser.core.id)

        // Then: Both threads are found
        assertTrue(threads.size >= 2)
        assertTrue(threads.any { it.thread.threadId == thread1Id })
        assertTrue(threads.any { it.thread.threadId == thread2Id })
    }

    @Test
    fun `test findByUserId returns empty list when user has no threads`() {
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
        val threads = threadRepository.findByUserId(anotherUser.core.id)

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
            userId = testUser.core.id,
            title = null,
            message = "Timestamp test",
            role = ThreadService.ROLE_USER
        )

        val afterCreate = java.time.Instant.now()

        // Then: All timestamps are within the expected range
        assertNotNull(created.thread.createdAt)
        assertTrue(created.thread.createdAt!! >= beforeCreate.minusMillis(100))
        assertTrue(created.thread.createdAt!! <= afterCreate.plusMillis(100))

        val turn = created.turns.first()
        assertNotNull(turn.turn.createdAt)
        assertNotNull(turn.current.createdAt)
    }

    @Test
    fun `test thread without title`() {
        // Given: We create a thread without a title
        val threadId = UUIDv7.generateString()

        // When: We create the thread with null title
        val created = threadRepository.createWithMessage(
            threadId = threadId,
            userId = testUser.core.id,
            title = null,
            message = "No title thread",
            role = ThreadService.ROLE_USER
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
            userId = testUser.core.id,
            title = "Delete Test",
            message = "Will be deleted",
            role = ThreadService.ROLE_USER
        )

        // When: We delete all threads
        threadRepository.deleteAll()

        // Then: The thread is no longer found
        val found = threadRepository.findByThreadId(threadId)
        assertFalse(found.isPresent)
    }

    @Test
    fun `test turn version has correct editor role`() {
        // Given: We create threads with different roles
        val userThreadId = UUIDv7.generateString()
        val assistantThreadId = UUIDv7.generateString()

        val userThread = threadRepository.createWithMessage(
            threadId = userThreadId,
            userId = testUser.core.id,
            title = null,
            message = "User message",
            role = ThreadService.ROLE_USER
        )

        val assistantThread = threadRepository.createWithMessage(
            threadId = assistantThreadId,
            userId = testUser.core.id,
            title = null,
            message = "Assistant message",
            role = ThreadService.ROLE_ASSISTANT
        )

        // Then: The editor role matches the turn role
        assertEquals(ThreadService.ROLE_USER, userThread.turns.first().current.editorRole)
        assertEquals(ThreadService.ROLE_ASSISTANT, assistantThread.turns.first().current.editorRole)
    }

    @Test
    fun `test createWithMessage throws when user not found`() {
        // When/Then: Creating a thread with non-existent user throws
        assertThrows(IllegalArgumentException::class.java) {
            threadRepository.createWithMessage(
                threadId = UUIDv7.generateString(),
                userId = "nonexistent-user-id",
                title = null,
                message = "Should fail",
                role = ThreadService.ROLE_USER
            )
        }
    }
}