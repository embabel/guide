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
import com.embabel.guide.chat.model.MessageData
import com.embabel.guide.chat.model.MessageVersionData
import com.embabel.guide.chat.model.MessageWithVersion
import com.embabel.guide.chat.service.ChatSessionService
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
 * Test for ChatSessionRepositoryImpl using the type-safe DSL.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
@ImportAutoConfiguration(exclude = [McpClientAutoConfiguration::class])
@Transactional
class ChatSessionRepositoryImplTest {

    @Autowired
    private lateinit var chatSessionRepository: ChatSessionRepositoryImpl

    @Autowired
    private lateinit var guideUserRepository: GuideUserRepository

    private lateinit var testUser: com.embabel.guide.domain.GuideUser

    @BeforeEach
    fun setUp() {
        // Create a test user for session ownership
        val guideUserData = GuideUserData(
            UUID.randomUUID().toString(),
            null,
            null
        )
        val webUserData = WebUserData(
            "session-test-${UUID.randomUUID()}",
            "Session Test User",
            "sessiontestuser-${UUID.randomUUID()}",
            "sessiontest@example.com",
            "hashedpassword",
            null
        )
        testUser = guideUserRepository.createWithWebUser(guideUserData, webUserData)
    }

    @Test
    fun `test create session with message and author`() {
        // Given: A session ID and message
        val sessionId = UUIDv7.generateString()
        val message = "Hello, this is a test message"

        // When: We create a session with the message and author
        val created = chatSessionRepository.createWithMessage(
            sessionId = sessionId,
            ownerId = testUser.core.id,
            title = "Test Session",
            message = message,
            role = ChatSessionService.ROLE_USER,
            authorId = testUser.core.id
        )

        // Then: The session is created with the correct data
        assertNotNull(created)
        assertEquals(sessionId, created.session.sessionId)
        assertEquals("Test Session", created.session.title)
        assertNotNull(created.session.createdAt)

        // And: The session has the correct owner
        assertEquals(testUser.core.id, created.owner.core.id)

        // And: The session has one message
        assertEquals(1, created.messages.size)
        val msg = created.messages.first()
        assertEquals(ChatSessionService.ROLE_USER, msg.message.role)
        assertEquals(message, msg.current.text)
        assertEquals(testUser.core.id, msg.authoredBy?.core?.id)
    }

    @Test
    fun `test create session with system message (no author)`() {
        // Given: A session ID and assistant message (system generated)
        val sessionId = UUIDv7.generateString()
        val message = "Welcome! How can I help you?"

        // When: We create a session with an assistant message and no author
        val created = chatSessionRepository.createWithMessage(
            sessionId = sessionId,
            ownerId = testUser.core.id,
            title = "Welcome",
            message = message,
            role = ChatSessionService.ROLE_ASSISTANT,
            authorId = null  // System message
        )

        // Then: The session is owned by the user
        assertEquals(testUser.core.id, created.owner.core.id)

        // And: The message has the assistant role but no author
        assertEquals(1, created.messages.size)
        assertEquals(ChatSessionService.ROLE_ASSISTANT, created.messages.first().message.role)
        assertEquals(message, created.messages.first().current.text)
        assertNull(created.messages.first().authoredBy)
    }

    @Test
    fun `test find session by ID`() {
        // Given: We create a session
        val sessionId = UUIDv7.generateString()
        chatSessionRepository.createWithMessage(
            sessionId = sessionId,
            ownerId = testUser.core.id,
            title = "Findable Session",
            message = "Test message",
            role = ChatSessionService.ROLE_USER,
            authorId = testUser.core.id
        )

        // When: We find by session ID
        val found = chatSessionRepository.findBySessionId(sessionId)

        // Then: The session is found with correct owner
        assertTrue(found.isPresent)
        assertEquals(sessionId, found.get().session.sessionId)
        assertEquals("Findable Session", found.get().session.title)
        assertEquals(testUser.core.id, found.get().owner.core.id)
        assertEquals(1, found.get().messages.size)
    }

    @Test
    fun `test findBySessionId returns empty when not found`() {
        // When: We search for a non-existent session
        val found = chatSessionRepository.findBySessionId("nonexistent-session-id")

        // Then: An empty Optional is returned
        assertFalse(found.isPresent)
    }

    @Test
    fun `test find sessions by owner ID`() {
        // Given: We create multiple sessions for the test user
        val session1Id = UUIDv7.generateString()
        val session2Id = UUIDv7.generateString()

        chatSessionRepository.createWithMessage(
            sessionId = session1Id,
            ownerId = testUser.core.id,
            title = "User Session 1",
            message = "First session message",
            role = ChatSessionService.ROLE_USER,
            authorId = testUser.core.id
        )

        chatSessionRepository.createWithMessage(
            sessionId = session2Id,
            ownerId = testUser.core.id,
            title = "User Session 2",
            message = "Second session message",
            role = ChatSessionService.ROLE_USER,
            authorId = testUser.core.id
        )

        // When: We find sessions by owner ID
        val sessions = chatSessionRepository.findByOwnerId(testUser.core.id)

        // Then: Both sessions are found
        assertTrue(sessions.size >= 2)
        assertTrue(sessions.any { it.session.sessionId == session1Id })
        assertTrue(sessions.any { it.session.sessionId == session2Id })
    }

    @Test
    fun `test findByOwnerId returns empty list when user has no sessions`() {
        // Given: A user with no sessions
        val anotherUser = guideUserRepository.createWithWebUser(
            GuideUserData(UUID.randomUUID().toString(), null, null),
            WebUserData(
                "no-sessions-${UUID.randomUUID()}",
                "No Sessions User",
                "nosessionsuser-${UUID.randomUUID()}",
                "nosessions@example.com",
                "hash",
                null
            )
        )

        // When: We find sessions for this user
        val sessions = chatSessionRepository.findByOwnerId(anotherUser.core.id)

        // Then: Empty list is returned
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `test session has correct timestamps`() {
        // Given: We create a session
        val sessionId = UUIDv7.generateString()
        val beforeCreate = java.time.Instant.now()

        val created = chatSessionRepository.createWithMessage(
            sessionId = sessionId,
            ownerId = testUser.core.id,
            title = null,
            message = "Timestamp test",
            role = ChatSessionService.ROLE_USER,
            authorId = testUser.core.id
        )

        val afterCreate = java.time.Instant.now()

        // Then: All timestamps are within the expected range
        assertNotNull(created.session.createdAt)
        assertTrue(created.session.createdAt!! >= beforeCreate.minusMillis(100))
        assertTrue(created.session.createdAt!! <= afterCreate.plusMillis(100))

        val msg = created.messages.first()
        assertNotNull(msg.message.createdAt)
        assertNotNull(msg.current.createdAt)
    }

    @Test
    fun `test session without title`() {
        // Given: We create a session without a title
        val sessionId = UUIDv7.generateString()

        // When: We create the session with null title
        val created = chatSessionRepository.createWithMessage(
            sessionId = sessionId,
            ownerId = testUser.core.id,
            title = null,
            message = "No title session",
            role = ChatSessionService.ROLE_USER,
            authorId = testUser.core.id
        )

        // Then: The session is created with null title
        assertNull(created.session.title)
    }

    @Test
    fun `test deleteAll removes all sessions`() {
        // Given: We create a session
        val sessionId = UUIDv7.generateString()
        chatSessionRepository.createWithMessage(
            sessionId = sessionId,
            ownerId = testUser.core.id,
            title = "Delete Test",
            message = "Will be deleted",
            role = ChatSessionService.ROLE_USER,
            authorId = testUser.core.id
        )

        // When: We delete all sessions
        chatSessionRepository.deleteAll()

        // Then: The session is no longer found
        val found = chatSessionRepository.findBySessionId(sessionId)
        assertFalse(found.isPresent)
    }

    @Test
    fun `test message version has correct editor role`() {
        // Given: We create sessions with different roles
        val userSessionId = UUIDv7.generateString()
        val assistantSessionId = UUIDv7.generateString()

        val userSession = chatSessionRepository.createWithMessage(
            sessionId = userSessionId,
            ownerId = testUser.core.id,
            title = null,
            message = "User message",
            role = ChatSessionService.ROLE_USER,
            authorId = testUser.core.id
        )

        val assistantSession = chatSessionRepository.createWithMessage(
            sessionId = assistantSessionId,
            ownerId = testUser.core.id,
            title = null,
            message = "Assistant message",
            role = ChatSessionService.ROLE_ASSISTANT,
            authorId = null
        )

        // Then: The editor role matches the message role
        assertEquals(ChatSessionService.ROLE_USER, userSession.messages.first().current.editorRole)
        assertEquals(ChatSessionService.ROLE_ASSISTANT, assistantSession.messages.first().current.editorRole)
    }

    @Test
    fun `test createWithMessage throws when owner not found`() {
        // When/Then: Creating a session with non-existent owner throws
        assertThrows(IllegalArgumentException::class.java) {
            chatSessionRepository.createWithMessage(
                sessionId = UUIDv7.generateString(),
                ownerId = "nonexistent-user-id",
                title = null,
                message = "Should fail",
                role = ChatSessionService.ROLE_USER,
                authorId = null
            )
        }
    }

    @Test
    fun `test addMessage adds message to existing session`() {
        // Given: A session with one message
        val sessionId = UUIDv7.generateString()
        val created = chatSessionRepository.createWithMessage(
            sessionId = sessionId,
            ownerId = testUser.core.id,
            title = "Add Message Test",
            message = "Initial message",
            role = ChatSessionService.ROLE_ASSISTANT,
            authorId = null
        )
        assertEquals(1, created.messages.size)

        // When: We add a reply message
        val replyMessage = MessageWithVersion(
            message = MessageData(
                messageId = UUIDv7.generateString(),
                sessionId = sessionId,
                role = ChatSessionService.ROLE_USER,
                createdAt = java.time.Instant.now()
            ),
            current = MessageVersionData(
                versionId = UUIDv7.generateString(),
                createdAt = java.time.Instant.now(),
                editorRole = ChatSessionService.ROLE_USER,
                reason = null,
                text = "This is my reply"
            ),
            authoredBy = testUser
        )
        val updated = chatSessionRepository.addMessage(sessionId, replyMessage)

        // Then: The session now has two messages
        assertEquals(2, updated.messages.size)
        assertEquals("Initial message", updated.messages[0].current.text)
        assertEquals("This is my reply", updated.messages[1].current.text)
        assertEquals(testUser.core.id, updated.messages[1].authoredBy?.core?.id)
    }

    @Test
    fun `test addMessage maintains chronological order`() {
        // Given: A session with one message
        val sessionId = UUIDv7.generateString()
        chatSessionRepository.createWithMessage(
            sessionId = sessionId,
            ownerId = testUser.core.id,
            title = "Ordering Test",
            message = "First message",
            role = ChatSessionService.ROLE_USER,
            authorId = testUser.core.id
        )

        // When: We add multiple messages in sequence
        val msg2 = MessageWithVersion(
            message = MessageData(
                messageId = UUIDv7.generateString(),
                sessionId = sessionId,
                role = ChatSessionService.ROLE_ASSISTANT,
                createdAt = java.time.Instant.now()
            ),
            current = MessageVersionData(
                versionId = UUIDv7.generateString(),
                createdAt = java.time.Instant.now(),
                editorRole = ChatSessionService.ROLE_ASSISTANT,
                reason = null,
                text = "Second message"
            ),
            authoredBy = null
        )
        chatSessionRepository.addMessage(sessionId, msg2)

        val msg3 = MessageWithVersion(
            message = MessageData(
                messageId = UUIDv7.generateString(),
                sessionId = sessionId,
                role = ChatSessionService.ROLE_USER,
                createdAt = java.time.Instant.now()
            ),
            current = MessageVersionData(
                versionId = UUIDv7.generateString(),
                createdAt = java.time.Instant.now(),
                editorRole = ChatSessionService.ROLE_USER,
                reason = null,
                text = "Third message"
            ),
            authoredBy = testUser
        )
        val finalSession = chatSessionRepository.addMessage(sessionId, msg3)

        // Then: Messages are in chronological order (by messageId which is UUIDv7)
        assertEquals(3, finalSession.messages.size)
        assertEquals("First message", finalSession.messages[0].current.text)
        assertEquals("Second message", finalSession.messages[1].current.text)
        assertEquals("Third message", finalSession.messages[2].current.text)
    }

    @Test
    fun `test addMessage throws when session not found`() {
        // Given: A message for a non-existent session
        val message = MessageWithVersion(
            message = MessageData(
                messageId = UUIDv7.generateString(),
                sessionId = "nonexistent-session",
                role = ChatSessionService.ROLE_USER,
                createdAt = java.time.Instant.now()
            ),
            current = MessageVersionData(
                versionId = UUIDv7.generateString(),
                createdAt = java.time.Instant.now(),
                editorRole = ChatSessionService.ROLE_USER,
                reason = null,
                text = "Should fail"
            ),
            authoredBy = testUser
        )

        // When/Then: Adding message to non-existent session throws
        assertThrows(IllegalArgumentException::class.java) {
            chatSessionRepository.addMessage("nonexistent-session", message)
        }
    }
}
