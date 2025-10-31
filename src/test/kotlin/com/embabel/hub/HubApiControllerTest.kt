package com.embabel.hub

import com.embabel.guide.Neo4jPropertiesInitializer
import com.embabel.guide.domain.GuideUserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.mcp.client.autoconfigure.McpClientAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.annotation.Rollback
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
@ImportAutoConfiguration(exclude = [McpClientAutoConfiguration::class])
class HubApiControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var guideUserRepository: GuideUserRepository

    @Autowired
    lateinit var jwtTokenService: JwtTokenService

    private val passwordEncoder = BCryptPasswordEncoder()

    @BeforeEach
    fun setUp() {
        // Clean up any existing test users to avoid conflicts
        guideUserRepository.deleteAll()
    }

    @Test
    fun `POST register should successfully create a new user`() {
        // Given
        val request = UserRegistrationRequest(
            userDisplayName = "Jane Doe",
            username = "janedoe",
            userEmail = "jane.doe@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )

        // When & Then
        val result = mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.displayName").value("Jane Doe"))
            .andExpect(jsonPath("$.username").value("janedoe"))
            .andExpect(jsonPath("$.email").value("jane.doe@example.com"))
            .andExpect(jsonPath("$.webUser").exists())
            .andExpect(jsonPath("$.webUser.userId").exists())
            .andExpect(jsonPath("$.webUser.refreshToken").exists())
            .andReturn()

        // Verify the password was properly hashed
        val responseBody = result.response.contentAsString
        val createdUser = objectMapper.readValue(responseBody, com.embabel.guide.domain.GuideUser::class.java)

        assertNotNull(createdUser.webUser)
        val webUser = createdUser.webUser!!
        assertNotEquals("SecurePassword123!", webUser.passwordHash)
        assertTrue(passwordEncoder.matches("SecurePassword123!", webUser.passwordHash))

        // Verify refresh token is valid
        val userId = jwtTokenService.validateRefreshToken(webUser.refreshToken)
        assertEquals(webUser.userId, userId)
    }

    @Test
    fun `POST register should return 400 when passwords do not match`() {
        // Given
        val request = UserRegistrationRequest(
            userDisplayName = "John Smith",
            username = "johnsmith",
            userEmail = "john.smith@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "DifferentPassword123!"
        )

        // When & Then
        mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Password and password confirmation do not match"))
    }

    @Test
    fun `POST register should return 400 when password is too short`() {
        // Given
        val request = UserRegistrationRequest(
            userDisplayName = "Bob Wilson",
            username = "bobwilson",
            userEmail = "bob.wilson@example.com",
            password = "Short1!",
            passwordConfirmation = "Short1!"
        )

        // When & Then
        mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Password must be at least 8 characters long"))
    }

    @Test
    fun `POST register should return 400 when username is blank`() {
        // Given
        val request = UserRegistrationRequest(
            userDisplayName = "Alice Johnson",
            username = "",
            userEmail = "alice.johnson@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )

        // When & Then
        mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Username is required"))
    }

    @Test
    fun `POST register should return 400 when email is blank`() {
        // Given
        val request = UserRegistrationRequest(
            userDisplayName = "Charlie Davis",
            username = "charliedavis",
            userEmail = "",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )

        // When & Then
        mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Email is required"))
    }

    @Test
    fun `POST register should return 400 when display name is blank`() {
        // Given
        val request = UserRegistrationRequest(
            userDisplayName = "",
            username = "davidmiller",
            userEmail = "david.miller@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )

        // When & Then
        mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Display name is required"))
    }

    @Test
    fun `POST register should return 400 when request body is malformed`() {
        // Given - malformed JSON
        val malformedJson = """{"userDisplayName": "Test", "username": "test"}"""

        // When & Then
        mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedJson)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST register should accept valid request with special characters in password`() {
        // Given
        val request = UserRegistrationRequest(
            userDisplayName = "Emma Brown",
            username = "emmabrown",
            userEmail = "emma.brown@example.com",
            password = "C0mpl3x!@#\$%^&*()",
            passwordConfirmation = "C0mpl3x!@#\$%^&*()"
        )

        // When & Then
        mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("Emma Brown"))
            .andExpect(jsonPath("$.username").value("emmabrown"))
    }

    @Test
    fun `POST register should persist user to database`() {
        // Given
        val request = UserRegistrationRequest(
            userDisplayName = "Frank Green",
            username = "frankgreen",
            userEmail = "frank.green@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )

        // When
        val result = mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andReturn()

        // Then - Verify user was persisted to database
        val responseBody = result.response.contentAsString
        val createdUser = objectMapper.readValue(responseBody, com.embabel.guide.domain.GuideUser::class.java)

        val foundUser = guideUserRepository.findById(createdUser.id)
        assertTrue(foundUser.isPresent)
        assertEquals("Frank Green", foundUser.get().displayName)
        assertEquals("frankgreen", foundUser.get().username)
        assertEquals("frank.green@example.com", foundUser.get().email)
    }

    // ========== Login Tests ==========

    @Test
    fun `POST login should successfully authenticate user with valid credentials`() {
        // Given - First register a user
        val registerRequest = UserRegistrationRequest(
            userDisplayName = "Test User",
            username = "testuser",
            userEmail = "test@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )
        mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest))
        )

        // When - Login with valid credentials
        val loginRequest = UserLoginRequest(
            username = "testuser",
            password = "SecurePassword123!"
        )

        val result = mockMvc.perform(
            post("/api/hub/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").exists())
            .andExpect(jsonPath("$.userId").exists())
            .andExpect(jsonPath("$.username").value("testuser"))
            .andExpect(jsonPath("$.displayName").value("Test User"))
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andReturn()

        // Verify the token is valid
        val responseBody = result.response.contentAsString
        val loginResponse = objectMapper.readValue(responseBody, LoginResponse::class.java)

        val userId = jwtTokenService.validateRefreshToken(loginResponse.token)
        assertEquals(loginResponse.userId, userId)
    }

    @Test
    fun `POST login should return 401 when username does not exist`() {
        // Given - No user registered
        val loginRequest = UserLoginRequest(
            username = "nonexistent",
            password = "SomePassword123!"
        )

        // When & Then
        mockMvc.perform(
            post("/api/hub/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Invalid username or password"))
    }

    @Test
    fun `POST login should return 401 when password is incorrect`() {
        // Given - Register a user first
        val registerRequest = UserRegistrationRequest(
            userDisplayName = "Password Test User",
            username = "passwordtest",
            userEmail = "passwordtest@example.com",
            password = "CorrectPassword123!",
            passwordConfirmation = "CorrectPassword123!"
        )
        mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest))
        )

        // When - Try to login with wrong password
        val loginRequest = UserLoginRequest(
            username = "passwordtest",
            password = "WrongPassword123!"
        )

        // Then
        mockMvc.perform(
            post("/api/hub/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Invalid username or password"))
    }

    @Test
    fun `POST login should return 401 when username is blank`() {
        // Given
        val loginRequest = UserLoginRequest(
            username = "",
            password = "SomePassword123!"
        )

        // When & Then
        mockMvc.perform(
            post("/api/hub/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Username is required"))
    }

    @Test
    fun `POST login should return 401 when password is blank`() {
        // Given
        val loginRequest = UserLoginRequest(
            username = "someuser",
            password = ""
        )

        // When & Then
        mockMvc.perform(
            post("/api/hub/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Password is required"))
    }

    @Test
    fun `POST login should return 400 when request body is malformed`() {
        // Given - malformed JSON
        val malformedJson = """{"username": "test"}"""

        // When & Then
        mockMvc.perform(
            post("/api/hub/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedJson)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST login should handle multiple login attempts with same credentials`() {
        // Given - Register a user
        val registerRequest = UserRegistrationRequest(
            userDisplayName = "Multi Login User",
            username = "multilogin",
            userEmail = "multilogin@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )
        mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest))
        )

        val loginRequest = UserLoginRequest(
            username = "multilogin",
            password = "SecurePassword123!"
        )

        // When - Login multiple times
        val firstLogin = mockMvc.perform(
            post("/api/hub/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest))
        )
            .andExpect(status().isOk)
            .andReturn()

        val secondLogin = mockMvc.perform(
            post("/api/hub/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest))
        )
            .andExpect(status().isOk)
            .andReturn()

        // Then - Both logins should return the same token
        val firstResponse = objectMapper.readValue(firstLogin.response.contentAsString, LoginResponse::class.java)
        val secondResponse = objectMapper.readValue(secondLogin.response.contentAsString, LoginResponse::class.java)

        assertEquals(firstResponse.token, secondResponse.token)
        assertEquals(firstResponse.userId, secondResponse.userId)
    }

    @Test
    fun `POST login should be case-sensitive for username`() {
        // Given - Register a user with lowercase username
        val registerRequest = UserRegistrationRequest(
            userDisplayName = "Case Test User",
            username = "casetest",
            userEmail = "casetest@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )
        mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest))
        )

        // When - Try to login with uppercase username
        val loginRequest = UserLoginRequest(
            username = "CASETEST",
            password = "SecurePassword123!"
        )

        // Then - Should fail because usernames are case-sensitive
        mockMvc.perform(
            post("/api/hub/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Invalid username or password"))
    }

    @Test
    fun `POST login should work with special characters in password`() {
        // Given - Register a user with special characters in password
        val registerRequest = UserRegistrationRequest(
            userDisplayName = "Special Char User",
            username = "specialchar",
            userEmail = "specialchar@example.com",
            password = "P@ssw0rd!#$%^&*()",
            passwordConfirmation = "P@ssw0rd!#$%^&*()"
        )
        mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest))
        )

        // When - Login with same special character password
        val loginRequest = UserLoginRequest(
            username = "specialchar",
            password = "P@ssw0rd!#$%^&*()"
        )

        // Then - Should succeed
        mockMvc.perform(
            post("/api/hub/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").exists())
            .andExpect(jsonPath("$.username").value("specialchar"))
    }
}
