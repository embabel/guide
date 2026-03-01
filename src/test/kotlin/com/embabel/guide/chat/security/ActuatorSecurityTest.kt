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
package com.embabel.guide.chat.security

import com.embabel.guide.Neo4jPropertiesInitializer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

/**
 * Tests that Actuator health endpoints are accessible without authentication.
 *
 * These tests ensure that health check endpoints remain publicly accessible
 * for monitoring, orchestration, and CI/CD workflows.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
class ActuatorSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `actuator health endpoint should be accessible without authentication`() {
        val result = mockMvc.perform(get("/actuator/health")).andReturn()
        val httpStatus = result.response.status
        // Health endpoint should not be blocked by security (401/403)
        // It may return 503 if some health indicators are DOWN, but security should not block it
        assert(httpStatus != 401 && httpStatus != 403) {
            "Actuator health endpoint should not return 401 or 403, got $httpStatus"
        }
    }

    @Test
    fun `actuator health endpoint should return health status`() {
        val result = mockMvc.perform(get("/actuator/health")).andReturn()
        val httpStatus = result.response.status
        // Should be 200 (UP) or 503 (DOWN), but accessible
        assert(httpStatus == 200 || httpStatus == 503) {
            "Actuator health endpoint should return 200 or 503, got $httpStatus"
        }
        // Response should contain status field
        val body = result.response.contentAsString
        assert(body.contains("status")) {
            "Health response should contain status field"
        }
    }

    @Test
    fun `actuator info endpoint should be accessible without authentication`() {
        val result = mockMvc.perform(get("/actuator/info")).andReturn()
        val httpStatus = result.response.status
        // Info endpoint might be empty (404) or return data (200), but should not be blocked by security (401/403)
        assert(httpStatus != 401 && httpStatus != 403) {
            "Actuator info endpoint should not return 401 or 403, got $httpStatus"
        }
    }

    @Test
    fun `actuator base path should be accessible`() {
        val result = mockMvc.perform(get("/actuator")).andReturn()
        val httpStatus = result.response.status
        // Should not be blocked by security
        assert(httpStatus != 401 && httpStatus != 403) {
            "Actuator base path should not return 401 or 403, got $httpStatus"
        }
    }
}
