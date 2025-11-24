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
package com.embabel.guide.domain.drivine

import com.embabel.guide.Neo4jPropertiesInitializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Test for DrivineGuideUserRepository using TestContainers.
 * This test demonstrates the Drivine composition-based approach compared to Neo4j OGM.
 *
 * Each test is @Transactional and marks the transaction for rollback at the start.
 * This will be improved in future Drivine versions with better @Rollback support.
 */
@SpringBootTest
@ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
@ImportAutoConfiguration(exclude = [McpClientAutoConfiguration::class])
@Transactional
class DrivineGuideUserRepositoryTest {

    @Autowired
    private lateinit var repository: DrivineGuideUserRepository

    @Test
    fun `test create and find GuideUser with Discord info`() {
        // Given: We create GuideUser data with Discord info
        val guideUserData = GuideUserData(
            UUID.randomUUID().toString(),
            "adaptive",
            null
        )

        val discordInfo = DiscordUserInfoData(
            "discord123",
            "testuser",
            "1234",
            "Test User",
            false,
            "https://example.com/avatar.png"
        )

        // When: We create the user via Drivine
        val created = repository.createWithDiscord(guideUserData, discordInfo)

        // Then: The user is created with composed data
        assertNotNull(created)
        assertEquals(guideUserData.id, created.guideUserData().id)
        assertEquals("adaptive", created.guideUserData().persona)
        assertEquals("discord123", created.discordUserInfo.id)
        assertEquals("testuser", created.discordUserInfo.username)

        // And: We can find it by Discord user ID
        val found = repository.findByDiscordUserId("discord123")
        assertTrue(found.isPresent)
        assertEquals(guideUserData.id, found.get().guideUserData().id)
        assertEquals("testuser", found.get().discordUserInfo.username)
    }

    @Test
    fun `test create and find GuideUser with WebUser info`() {
        // Given: We create GuideUser data with WebUser info
        val guideUserData = GuideUserData(
            UUID.randomUUID().toString(),
            "adaptive",
            "Answer questions about embabel"
        )

        val webUserData = WebUserData(
            "web123",
            "Web Test User",
            "webtestuser",
            "test@example.com",
            "hashedpassword",
            null
        )

        // When: We create the user via Drivine
        val created = repository.createWithWebUser(guideUserData, webUserData)

        // Then: The user is created with composed data
        assertNotNull(created)
        assertEquals(guideUserData.id, created.guideUserData().id)
        assertEquals("Answer questions about embabel", created.guideUserData().customPrompt)
        assertEquals("web123", created.webUser.id)
        assertEquals("webtestuser", created.webUser.userName)

        // And: We can find it by web user ID
        val found = repository.findByWebUserId("web123")
        assertTrue(found.isPresent)
        assertEquals(guideUserData.id, found.get().guideUserData().id)
        assertEquals("test@example.com", found.get().webUser.userEmail)
    }

    @Test
    fun `test find by web username`() {
        // Given: We create a GuideUser with a specific username
        val guideUserData = GuideUserData(
            UUID.randomUUID().toString(),
            null,
            null
        )

        val webUserData = WebUserData(
            "web456",
            "Username Test",
            "uniqueusername",
            "unique@example.com",
            "hashed",
            null
        )

        repository.createWithWebUser(guideUserData, webUserData)

        // When: We search by username
        val found = repository.findByWebUserName("uniqueusername")

        // Then: The user is found
        assertTrue(found.isPresent)
        assertEquals(guideUserData.id, found.get().guideUserData().id)
        assertEquals("uniqueusername", found.get().webUser.userName)
    }

    @Test
    fun `test update persona`() {
        // Given: We create a GuideUser
        val guideUserData = GuideUserData(
            UUID.randomUUID().toString(),
            "adaptive",
            null
        )

        val discordInfo = DiscordUserInfoData(
            "discord789",
            "personatest",
            "0001",
            "Persona Test",
            false,
            null
        )

        val created = repository.createWithDiscord(guideUserData, discordInfo)
        val userId = created.guideUserData().id

        // When: We update the persona
        repository.updatePersona(userId, "expert")

        // Then: The persona is updated
        val found = repository.findByDiscordUserId("discord789")
        assertTrue(found.isPresent)
        assertEquals("expert", found.get().guideUserData().persona)
    }

    @Test
    fun `test update custom prompt`() {
        // Given: We create a GuideUser
        val guideUserData = GuideUserData(
            UUID.randomUUID().toString(),
            null,
            null
        )

        val webUserData = WebUserData(
            "web999",
            "Prompt Test",
            "prompttest",
            "prompt@example.com",
            "hash",
            null
        )

        val created = repository.createWithWebUser(guideUserData, webUserData)
        val userId = created.guideUserData().id

        // When: We update the custom prompt
        repository.updateCustomPrompt(userId, "Updated prompt")

        // Then: The custom prompt is updated
        val found = repository.findByWebUserId("web999")
        assertTrue(found.isPresent)
        assertEquals("Updated prompt", found.get().guideUserData().customPrompt)
    }

    @Test
    fun `test findByDiscordUserId returns empty when not found`() {
        // When: We search for a non-existent Discord user
        val found = repository.findByDiscordUserId("nonexistent")

        // Then: An empty Optional is returned
        assertFalse(found.isPresent)
    }

    @Test
    fun `test findByWebUserId returns empty when not found`() {
        // When: We search for a non-existent web user
        val found = repository.findByWebUserId("nonexistent")

        // Then: An empty Optional is returned
        assertFalse(found.isPresent)
    }

    @Test
    fun `test composition returns flat data structures`() {
        // Given: We create a GuideUser with WebUser
        val guideUserData = GuideUserData(
            UUID.randomUUID().toString(),
            "jesse",
            null
        )

        val webUserData = WebUserData(
            "flat123",
            "Flat Test",
            "flattest",
            "flat@example.com",
            "flathash",
            "token123"
        )

        val created = repository.createWithWebUser(guideUserData, webUserData)

        // Then: The result is a flat composition (no nested OGM relationships)
        // Both pieces of data are directly accessible
        assertNotNull(created.guideUserData())
        assertNotNull(created.webUser)

        // And: All properties are accessible without lazy loading
        assertEquals("jesse", created.guideUserData().persona)
        assertEquals(null, created.guideUserData().customPrompt)
        assertEquals("flattest", created.webUser.userName)
        assertEquals("flat@example.com", created.webUser.userEmail)
        assertEquals("token123", created.webUser.refreshToken)
    }
}
