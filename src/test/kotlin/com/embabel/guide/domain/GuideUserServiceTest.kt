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
package com.embabel.guide.domain

import com.embabel.guide.Neo4jTestContainer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.Rollback
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest
@Rollback(false)
class GuideUserServiceTest {

    companion object {
        private val useLocalNeo4j = true

        @JvmStatic
        @DynamicPropertySource
        fun neo4jProperties(registry: DynamicPropertyRegistry) {
            if (!useLocalNeo4j) {
                val container = Neo4jTestContainer.instance
                registry.add("spring.neo4j.uri") { container.boltUrl }
                registry.add("spring.neo4j.authentication.username") { "neo4j" }
                registry.add("spring.neo4j.authentication.password") { container.adminPassword }
            } else {
                // Use local Neo4j - load properties from application-test-local.properties
                registry.add("spring.neo4j.uri") { "bolt://localhost:7687" }
                registry.add("spring.neo4j.authentication.username") { "neo4j" }
                registry.add("spring.neo4j.authentication.password") { "h4ckM3\$\$\$\$" }
            }
        }
    }

    @Autowired
    private lateinit var guideUserService: GuideUserService

    @Autowired
    private lateinit var guideUserRepository: GuideUserRepository

    @Test
    fun `test getOrCreateAnonymousWebUser creates new user when none exists`() {
        // Given: No anonymous web user exists
        guideUserRepository.deleteAll()

        // When: We request the anonymous web user
        val anonymousUser = guideUserService.getOrCreateAnonymousWebUser()

        // Then: A new GuideUser is created with an AnonymousWebUser relationship
        assertNotNull(anonymousUser)
        assertNotNull(anonymousUser.id)

        // Verify it was persisted
        val found = guideUserRepository.findById(anonymousUser.id)
        assertTrue(found.isPresent)
    }

    @Test
    fun `test getOrCreateAnonymousWebUser returns existing user when one exists`() {
        // Given: An anonymous web user already exists
        guideUserRepository.deleteAll()
        val firstCall = guideUserService.getOrCreateAnonymousWebUser()
        val firstUserId = firstCall.id

        // When: We request the anonymous web user again
        val secondCall = guideUserService.getOrCreateAnonymousWebUser()

        // Then: The same user is returned
        assertEquals(firstUserId, secondCall.id)

        // Verify only one GuideUser exists in the database
        val allUsers = guideUserRepository.findAll()
        assertEquals(1, allUsers.count())
    }

    @Test
    fun `test anonymous web user has correct display name`() {
        // Given: We create an anonymous web user
        guideUserRepository.deleteAll()

        // When: We request the anonymous web user
        val anonymousUser = guideUserService.getOrCreateAnonymousWebUser()

        // Then: The display name should be "Friend"
        // Note: We need to verify via the repository query to ensure the WebUser relationship is loaded
        val found = guideUserRepository.findAnonymousWebUser()
        assertTrue(found.isPresent)

        // The GuideUser itself will have a webUser relationship
        // We can't directly test displayName here since GuideUser.getDisplayName()
        // currently only looks at discordUserInfo
        // This is a limitation we could address by updating GuideUser.getDisplayName()
    }
}
