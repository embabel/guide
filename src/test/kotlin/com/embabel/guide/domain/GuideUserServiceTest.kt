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

import com.embabel.guide.Neo4jPropertiesInitializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
@ImportAutoConfiguration(exclude = [McpClientAutoConfiguration::class])
@Transactional
open class GuideUserServiceTest {

    @Autowired
    private lateinit var guideUserService: GuideUserService

    @Autowired
    private lateinit var guideUserRepository: GuideUserRepository

    @Test
    fun `test getOrCreateAnonymousWebUser creates new user when none exists`() {
        // Given: fresh transaction — no anonymous web user exists yet
        // (system personas are seeded at startup by PersonaSeedingService)

        // When: We request the anonymous web user
        val anonymousUser = guideUserService.findOrCreateAnonymousWebUser()

        // Then: A new GuideUser is created with an AnonymousWebUser relationship
        assertNotNull(anonymousUser)
        assertNotNull(anonymousUser.guideUserData().id)

        // Verify it was persisted
        val found = guideUserRepository.findById(anonymousUser.guideUserData().id)
        assertTrue(found.isPresent)
    }

    @Test
    fun `test getOrCreateAnonymousWebUser returns existing user when one exists`() {
        // Given: An anonymous web user already exists
        val firstCall = guideUserService.findOrCreateAnonymousWebUser()
        val firstUserId = firstCall.guideUserData().id

        // When: We request the anonymous web user again
        val secondCall = guideUserService.findOrCreateAnonymousWebUser()

        // Then: The same user is returned
        assertEquals(firstUserId, secondCall.guideUserData().id)
    }

    @Test
    fun `test anonymous web user has correct display name`() {
        // When: We request the anonymous web user
        val anonymousUser = guideUserService.findOrCreateAnonymousWebUser()

        // Then: The display name should be "Friend"
        val found = guideUserRepository.findAnonymousWebUser().orElseThrow()
        assertEquals("Friend", found.core.displayName)
    }
}