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
import com.embabel.guide.domain.*
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
 * Test for GraphObjectGuideUserRepository using the type-safe DSL.
 * This test mirrors DrivineGuideUserRepositoryTest but uses the new GraphView-based repository.
 *
 * Tests will fail until each method is implemented - this is intentional TDD.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
@ImportAutoConfiguration(exclude = [McpClientAutoConfiguration::class])
@Transactional
class GuideUserRepositoryDefaultImplTest {

    @Autowired
    private lateinit var repository: GuideUserRepositoryDefaultImpl

    @Test
    fun `test create and find GuideUser with Discord info`() {
        // Given: We create GuideUser data with Discord info
        val guideUserData = GuideUserData(
            id = UUID.randomUUID().toString(),
            displayName = "Test User",
            persona = "adaptive"
        )

        val discordInfo = DiscordUserInfoData(
            "graphobj-discord-${UUID.randomUUID()}",
            "testuser",
            "1234",
            "Test User",
            false,
            "https://example.com/avatar.png"
        )

        // When: We create the user via the repository
        val created = repository.createWithDiscord(guideUserData, discordInfo)

        // Then: The user is created with composed data
        assertNotNull(created)
        assertEquals(guideUserData.id, created.guideUserData().id)
        assertEquals("adaptive", created.guideUserData().persona)
        assertEquals(discordInfo.id, created.discordUserInfo?.id)
        assertEquals("testuser", created.discordUserInfo?.username)

        // And: We can find it by Discord user ID
        val found = repository.findByDiscordUserId(discordInfo.id!!)
        assertTrue(found.isPresent)
        assertEquals(guideUserData.id, found.get().guideUserData().id)
        assertEquals("testuser", found.get().discordUserInfo?.username)
    }

    @Test
    fun `test create and find GuideUser with WebUser info`() {
        // Given: We create GuideUser data with WebUser info
        val guideUserData = GuideUserData(
            id = UUID.randomUUID().toString(),
            displayName = "Web Test User",
            persona = "adaptive",
            customPrompt = "Answer questions about embabel"
        )

        val webUserData = WebUserData(
            "graphobj-web-${UUID.randomUUID()}",
            "Web Test User",
            "webtestuser",
            "test@example.com",
            "hashedpassword",
            null
        )

        // When: We create the user via the repository
        val created = repository.createWithWebUser(guideUserData, webUserData)

        // Then: The user is created with composed data
        assertNotNull(created)
        assertEquals(guideUserData.id, created.guideUserData().id)
        assertEquals("Answer questions about embabel", created.guideUserData().customPrompt)
        assertEquals(webUserData.id, created.webUser?.id)
        assertEquals("webtestuser", created.webUser?.userName)

        // And: We can find it by web user ID
        val found = repository.findByWebUserId(webUserData.id)
        assertTrue(found.isPresent)
        assertEquals(guideUserData.id, found.get().guideUserData().id)
        assertEquals("test@example.com", found.get().webUser?.userEmail)
    }

    @Test
    fun `test find by web username`() {
        // Given: We create a GuideUser with a specific username
        val guideUserData = GuideUserData(
            id = UUID.randomUUID().toString(),
            displayName = "Username Test"
        )

        val webUserData = WebUserData(
            "graphobj-web-${UUID.randomUUID()}",
            "Username Test",
            "graphobj-uniqueuser-${UUID.randomUUID()}",
            "unique@example.com",
            "hashed",
            null
        )

        repository.createWithWebUser(guideUserData, webUserData)

        // When: We search by username
        val found = repository.findByWebUserName(webUserData.userName)

        // Then: The user is found
        assertTrue(found.isPresent)
        assertEquals(guideUserData.id, found.get().guideUserData().id)
        assertEquals(webUserData.userName, found.get().webUser?.userName)
    }

    @Test
    fun `test update persona`() {
        // Given: We create a GuideUser
        val guideUserData = GuideUserData(
            id = UUID.randomUUID().toString(),
            displayName = "Persona Test",
            persona = "adaptive"
        )

        val discordInfo = DiscordUserInfoData(
            "graphobj-discord-${UUID.randomUUID()}",
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
        val found = repository.findByDiscordUserId(discordInfo.id!!)
        assertTrue(found.isPresent)
        assertEquals("expert", found.get().guideUserData().persona)
    }

    @Test
    fun `test update persona for web user`() {
        // Given: We create a GuideUser with a WebUser (same as frontend flow)
        val guideUserData = GuideUserData(
            id = UUID.randomUUID().toString(),
            displayName = "Web Persona Test"
        )

        val webUserData = WebUserData(
            "graphobj-web-persona-${UUID.randomUUID()}",
            "Web Persona Test",
            "webpersonatest",
            "persona@example.com",
            "hashedpassword",
            null
        )

        val created = repository.createWithWebUser(guideUserData, webUserData)
        val userId = created.guideUserData().id
        assertNull(created.guideUserData().persona)

        // When: We update the persona (same code path as HubService)
        repository.updatePersona(userId, "expert")

        // Then: The persona is updated when reading by core ID
        val foundById = repository.findById(userId)
        assertTrue(foundById.isPresent)
        assertEquals("expert", foundById.get().guideUserData().persona)

        // And: Also updated when reading by web user ID (the frontend lookup path)
        val foundByWebId = repository.findByWebUserId(webUserData.id)
        assertTrue(foundByWebId.isPresent)
        assertEquals("expert", foundByWebId.get().guideUserData().persona)
    }

    @Test
    fun `test update custom prompt`() {
        // Given: We create a GuideUser
        val guideUserData = GuideUserData(
            id = UUID.randomUUID().toString(),
            displayName = "Prompt Test"
        )

        val webUserData = WebUserData(
            "graphobj-web-${UUID.randomUUID()}",
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
        val found = repository.findByWebUserId(webUserData.id)
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
    fun `test findById returns GuideUser`() {
        // Given: We create a GuideUser
        val guideUserData = GuideUserData(
            id = UUID.randomUUID().toString(),
            displayName = "FindById Test",
            persona = "jesse"
        )

        val discordInfo = DiscordUserInfoData(
            "graphobj-discord-${UUID.randomUUID()}",
            "findbyidtest",
            "0001",
            "FindById Test",
            false,
            null
        )

        repository.createWithDiscord(guideUserData, discordInfo)

        // When: We find by GuideUser ID
        val found = repository.findById(guideUserData.id)

        // Then: The user is found
        assertTrue(found.isPresent)
        assertEquals(guideUserData.id, found.get().guideUserData().id)
        assertEquals("jesse", found.get().guideUserData().persona)
    }

    @Test
    fun `test save updates GuideUser`() {
        // Given: We create a GuideUser
        val guideUserData = GuideUserData(
            id = UUID.randomUUID().toString(),
            displayName = "Save Test",
            persona = "original"
        )

        val discordInfo = DiscordUserInfoData(
            "graphobj-discord-${UUID.randomUUID()}",
            "savetest",
            "0001",
            "Save Test",
            false,
            null
        )

        val created = repository.createWithDiscord(guideUserData, discordInfo)

        // When: We modify and save
        val modified = created.copy(
            core = created.core.copy(persona = "modified", customPrompt = "new prompt")
        )
        val saved = repository.save(modified)

        // Then: The changes are persisted
        assertEquals("modified", saved.guideUserData().persona)
        assertEquals("new prompt", saved.guideUserData().customPrompt)

        // And: Can be retrieved
        val found = repository.findById(guideUserData.id)
        assertTrue(found.isPresent)
        assertEquals("modified", found.get().guideUserData().persona)
    }

    @Test
    fun `test findAll returns all GuideUsers`() {
        // Given: We create multiple GuideUsers
        val user1 = repository.createWithDiscord(
            GuideUserData(id = UUID.randomUUID().toString(), displayName = "User 1", persona = "user1"),
            DiscordUserInfoData("graphobj-discord-${UUID.randomUUID()}", "user1", "0001", "User 1", false, null)
        )
        val user2 = repository.createWithWebUser(
            GuideUserData(id = UUID.randomUUID().toString(), displayName = "User 2", persona = "user2"),
            WebUserData("graphobj-web-${UUID.randomUUID()}", "User 2", "user2", "user2@test.com", "hash", null)
        )

        // When: We find all
        val all = repository.findAll()

        // Then: Both users are returned
        assertTrue(all.size >= 2)
        assertTrue(all.any { it.guideUserData().id == user1.guideUserData().id })
        assertTrue(all.any { it.guideUserData().id == user2.guideUserData().id })
    }

    @Test
    fun `test deleteAll removes all GuideUsers`() {
        // Given: We create a GuideUser
        val guideUserData = GuideUserData(id = UUID.randomUUID().toString(), displayName = "Delete Test")
        val discordInfo = DiscordUserInfoData(
            "graphobj-discord-${UUID.randomUUID()}",
            "deletetest",
            "0001",
            "Delete Test",
            false,
            null
        )
        repository.createWithDiscord(guideUserData, discordInfo)

        // When: We delete all
        repository.deleteAll()

        // Then: No users remain
        val all = repository.findAll()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `test deleteByUsernameStartingWith removes matching users`() {
        // Given: We create users with specific username prefixes
        val prefix = "graphobj-deleteprefix-${UUID.randomUUID()}"

        repository.createWithWebUser(
            GuideUserData(id = UUID.randomUUID().toString(), displayName = "User 1"),
            WebUserData("web1", "User 1", "${prefix}-user1", "user1@test.com", "hash", null)
        )
        repository.createWithWebUser(
            GuideUserData(id = UUID.randomUUID().toString(), displayName = "User 2"),
            WebUserData("web2", "User 2", "${prefix}-user2", "user2@test.com", "hash", null)
        )
        repository.createWithWebUser(
            GuideUserData(id = UUID.randomUUID().toString(), displayName = "Other User"),
            WebUserData("web3", "Other User", "other-user", "other@test.com", "hash", null)
        )

        // When: We delete by prefix
        repository.deleteByUsernameStartingWith(prefix)

        // Then: Matching users are deleted, others remain
        assertFalse(repository.findByWebUserName("${prefix}-user1").isPresent)
        assertFalse(repository.findByWebUserName("${prefix}-user2").isPresent)
        assertTrue(repository.findByWebUserName("other-user").isPresent)
    }

    @Test
    fun `test find anonymous web user`() {
        // Given: We create an anonymous web user
        val guideUserData = GuideUserData(id = UUID.randomUUID().toString(), displayName = "Friend")
        val anonymousUser = AnonymousWebUserData(
            "anon-${UUID.randomUUID()}",
            "Friend",
            "anonymous",
            null,
            null,
            null
        )
        repository.createWithWebUser(guideUserData, anonymousUser)

        // And: A regular web user
        repository.createWithWebUser(
            GuideUserData(id = UUID.randomUUID().toString(), displayName = "Regular User"),
            WebUserData("regular-${UUID.randomUUID()}", "Regular User", "regular", null, null, null)
        )

        // When: We search for the anonymous web user
        val found = repository.findAnonymousWebUser()

        // Then: The anonymous user is found (matched by Anonymous label in graph)
        assertTrue(found.isPresent)
        assertEquals(guideUserData.id, found.get().guideUserData().id)
        assertEquals("Friend", found.get().webUser?.displayName)
        assertEquals("anonymous", found.get().webUser?.userName)
    }

    @Test
    fun `test findAnonymousWebUser returns empty when none exists`() {
        // Given: Only regular web users exist
        repository.createWithWebUser(
            GuideUserData(id = UUID.randomUUID().toString(), displayName = "Regular User"),
            WebUserData("regular-${UUID.randomUUID()}", "Regular User", "regular", null, null, null)
        )

        // When: We search for anonymous user
        val found = repository.findAnonymousWebUser()

        // Then: Empty is returned
        assertFalse(found.isPresent)
    }
}
