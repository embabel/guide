package com.embabel.hub.integrations

import com.embabel.guide.Neo4jPropertiesInitializer
import tools.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
@ImportAutoConfiguration(exclude = [McpClientAutoConfiguration::class])
class KeyValidationEndpointTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockitoBean
    lateinit var userModelFactory: UserModelFactory

    @Test
    fun `POST keys-validate returns valid true when key is good`() {
        `when`(userModelFactory.validateKey(LlmProvider.OPENAI, "sk-good-key"))
            .thenReturn(null)

        val body = objectMapper.writeValueAsString(
            ValidateKeyRequest(provider = LlmProvider.OPENAI, key = "sk-good-key")
        )

        mockMvc.perform(
            post("/api/hub/integrations/keys/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.provider").value("OPENAI"))
            .andExpect(jsonPath("$.error").doesNotExist())
    }

    @Test
    fun `POST keys-validate returns valid false with error when key is bad`() {
        `when`(userModelFactory.validateKey(LlmProvider.ANTHROPIC, "sk-bad-key"))
            .thenReturn("Invalid API key")

        val body = objectMapper.writeValueAsString(
            ValidateKeyRequest(provider = LlmProvider.ANTHROPIC, key = "sk-bad-key")
        )

        mockMvc.perform(
            post("/api/hub/integrations/keys/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.provider").value("ANTHROPIC"))
            .andExpect(jsonPath("$.error").value("Invalid API key"))
    }

    @Test
    fun `POST keys-validate works for all providers`() {
        for (provider in LlmProvider.entries) {
            `when`(userModelFactory.validateKey(provider, "test-key"))
                .thenReturn(null)

            val body = objectMapper.writeValueAsString(
                ValidateKeyRequest(provider = provider, key = "test-key")
            )

            mockMvc.perform(
                post("/api/hub/integrations/keys/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.provider").value(provider.name))
        }
    }

    @Test
    fun `POST keys-validate does not require authentication`() {
        `when`(userModelFactory.validateKey(LlmProvider.OPENAI, "sk-anon"))
            .thenReturn(null)

        val body = objectMapper.writeValueAsString(
            ValidateKeyRequest(provider = LlmProvider.OPENAI, key = "sk-anon")
        )

        // No Authorization header — should still succeed
        mockMvc.perform(
            post("/api/hub/integrations/keys/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(true))
    }
}