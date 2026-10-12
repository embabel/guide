package com.embabel.hub.integrations

import com.embabel.guide.Neo4jPropertiesInitializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withForbiddenRequest
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.client.RestClient

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
@ImportAutoConfiguration(exclude = [McpClientAutoConfiguration::class])
@Import(DeepgramProxyControllerTest.Config::class)
class DeepgramProxyControllerTest {

    @TestConfiguration
    class Config {
        private val builder = RestClient.builder().baseUrl("https://api.deepgram.com")

        @Bean
        fun mockRestServiceServer(): MockRestServiceServer =
            MockRestServiceServer.bindTo(builder).build()

        @Bean
        @Primary
        @Qualifier("deepgramRestClient")
        fun testDeepgramRestClient(mockRestServiceServer: MockRestServiceServer): RestClient =
            builder.build()
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var mockServer: MockRestServiceServer

    // The server is one bean shared by every test here, and refuses new expectations once it has
    // served a request, so each test starts it afresh whatever order they run in.
    @BeforeEach
    fun resetMockServer() {
        mockServer.reset()
    }

    @Test
    fun `returns 401 when x-deepgram-key header is missing`() {
        mockMvc.perform(get("/api/v1/deepgram/models"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Missing Deepgram API key"))
    }

    @Test
    fun `returns 401 when x-deepgram-key header is blank`() {
        mockMvc.perform(
            get("/api/v1/deepgram/models")
                .header("x-deepgram-key", "")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Missing Deepgram API key"))
    }

    @Test
    fun `proxies successful response from Deepgram`() {
        val deepgramResponse = """{"stt":[{"name":"nova-2","version":"latest"}]}"""

        mockServer.expect(requestTo("https://api.deepgram.com/v1/models"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Token test-api-key"))
            .andRespond(withSuccess(deepgramResponse, MediaType.APPLICATION_JSON))

        mockMvc.perform(
            get("/api/v1/deepgram/models")
                .header("x-deepgram-key", "test-api-key")
        )
            .andExpect(status().isOk)
            .andExpect(content().json(deepgramResponse))

        mockServer.verify()
    }

    @Test
    fun `returns upstream status on Deepgram error`() {
        mockServer.expect(requestTo("https://api.deepgram.com/v1/models"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withUnauthorizedRequest())

        mockMvc.perform(
            get("/api/v1/deepgram/models")
                .header("x-deepgram-key", "bad-key")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Failed to fetch models"))

        mockServer.verify()
    }

    @Test
    fun `returns 403 when Deepgram returns forbidden`() {
        mockServer.expect(requestTo("https://api.deepgram.com/v1/models"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withForbiddenRequest())

        mockMvc.perform(
            get("/api/v1/deepgram/models")
                .header("x-deepgram-key", "restricted-key")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("Failed to fetch models"))

        mockServer.verify()
    }
}