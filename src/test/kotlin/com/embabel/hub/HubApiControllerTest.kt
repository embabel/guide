package com.embabel.hub

import com.embabel.guide.TestApplicationContext
import com.embabel.guide.domain.GuideUserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
@AutoConfigureMockMvc
@ContextConfiguration(classes = [TestApplicationContext::class])
@Rollback(false)
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
}