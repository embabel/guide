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

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Regression test for MCP endpoint security configuration.
 *
 * Ensures that MCP endpoints are NOT blocked by Spring Security.
 * These endpoints must be publicly accessible for MCP clients like Cursor to connect.
 *
 * Context: We use WebSecurityCustomizer to bypass Spring Security for MCP paths.
 * If this test fails with 401 or 403, check SecurityConfig mcpSecurityCustomizer bean.
 */
@SpringBootTest
@AutoConfigureMockMvc
class McpSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `MCP SSE endpoint should be accessible without authentication`() {
        mockMvc.perform(get("/sse"))
            .andExpect(status().isOk)
    }

    @Test
    fun `MCP endpoint should be accessible without authentication`() {
        val result = mockMvc.perform(get("/mcp"))
            .andReturn()

        val httpStatus = result.response.status
        assert(httpStatus != 401 && httpStatus != 403) {
            "MCP endpoint returned $httpStatus but expected not 401 or 403"
        }
    }

    @Test
    fun `MCP tools list endpoint should be accessible without authentication`() {
        val result = mockMvc.perform(get("/mcp/tools/list"))
            .andReturn()

        val httpStatus = result.response.status
        assert(httpStatus != 401 && httpStatus != 403) {
            "MCP tools endpoint returned $httpStatus but expected not 401 or 403"
        }
    }
}
