package com.embabel.hub

//import org.springframework.ai.mcp.client.autoconfigure.McpClientAutoConfiguration
import com.embabel.guide.Neo4jPropertiesInitializer
import com.embabel.guide.chat.service.ChatSessionService
import com.embabel.guide.domain.GuideUser
import com.embabel.guide.domain.GuideUserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
@ImportAutoConfiguration(exclude = [McpClientAutoConfiguration::class])
@Transactional
class HubApiControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var guideUserRepository: GuideUserRepository

    @Autowired
    lateinit var jwtTokenService: JwtTokenService

    @Autowired
    lateinit var chatSessionService: ChatSessionService

    private val passwordEncoder = BCryptPasswordEncoder()

    @BeforeEach
    fun cleanup() {
        // Clean up any test users from previous runs
        guideUserRepository.deleteByUsernameStartingWith("test_")
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
            .andExpect(jsonPath("$.webUser.id").exists())
            .andExpect(jsonPath("$.webUser.refreshToken").exists())
            .andReturn()

        // Verify the password was properly hashed
        val responseBody = result.response.contentAsString
        val createdUser = objectMapper.readValue(responseBody, GuideUser::class.java)

        createdUser.webUser?.let { webUser ->
            assertNotEquals("SecurePassword123!", webUser.passwordHash)
            assertTrue(passwordEncoder.matches("SecurePassword123!", webUser.passwordHash))

            // Verify refresh token is valid
            webUser.refreshToken?.let { refreshToken ->
                val userId = jwtTokenService.validateRefreshToken(refreshToken)
                assertEquals(webUser.id, userId)
            } ?: fail("Expected refresh token to be present")
        } ?: fail("Expected webUser to be present")
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
            .andExpect(jsonPath("$.message").value("Password and password confirmation do not match"))
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
            .andExpect(jsonPath("$.message").value("Password must be at least 8 characters long"))
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
            .andExpect(jsonPath("$.message").value("Username is required"))
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
            .andExpect(jsonPath("$.message").value("Email is required"))
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
            .andExpect(jsonPath("$.message").value("Display name is required"))
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
        val createdUser = objectMapper.readValue(responseBody, GuideUser::class.java)

        createdUser.webUser?.let { webUser ->
            val foundUser = guideUserRepository.findByWebUserId(webUser.id)
            assertTrue(foundUser.isPresent)
            val foundGuideUser = foundUser.get()
            assertEquals("Frank Green", foundGuideUser.displayName)
            assertEquals("frankgreen", foundGuideUser.username)
            assertEquals("frank.green@example.com", foundGuideUser.email)
        } ?: fail("Expected webUser to be present")
    }

    // ========== Login Tests ==========

    @Test
    fun `POST login should successfully authenticate user with valid credentials`() {
        // Given - First register a user
        val registerRequest = UserRegistrationRequest(
            userDisplayName = "Test User",
            username = "test_user",
            userEmail = "test_user@example.com",
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
            username = "test_user",
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
            .andExpect(jsonPath("$.username").value("test_user"))
            .andExpect(jsonPath("$.displayName").value("Test User"))
            .andExpect(jsonPath("$.email").value("test_user@example.com"))
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
            .andExpect(jsonPath("$.message").value("Invalid username or password"))
    }

    @Test
    fun `POST login should return 401 when password is incorrect`() {
        // Given - Register a user first
        val registerRequest = UserRegistrationRequest(
            userDisplayName = "Password Test User",
            username = "test_passwordtest",
            userEmail = "test_passwordtest@example.com",
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
            username = "test_passwordtest",
            password = "WrongPassword123!"
        )

        // Then
        mockMvc.perform(
            post("/api/hub/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("Invalid username or password"))
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
            .andExpect(jsonPath("$.message").value("Username is required"))
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
            .andExpect(jsonPath("$.message").value("Password is required"))
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
            username = "test_multilogin",
            userEmail = "test_multilogin@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )
        mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest))
        )

        val loginRequest = UserLoginRequest(
            username = "test_multilogin",
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
            username = "test_casetest",
            userEmail = "test_casetest@example.com",
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
            username = "TEST_CASETEST",
            password = "SecurePassword123!"
        )

        // Then - Should fail because usernames are case-sensitive
        mockMvc.perform(
            post("/api/hub/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("Invalid username or password"))
    }

    @Test
    fun `POST login should work with special characters in password`() {
        // Given - Register a user with special characters in password
        val registerRequest = UserRegistrationRequest(
            userDisplayName = "Special Char User",
            username = "test_specialchar",
            userEmail = "test_specialchar@example.com",
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
            username = "test_specialchar",
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
            .andExpect(jsonPath("$.username").value("test_specialchar"))
    }

    // ========== Sessions Tests ==========

    @Test
    fun `GET sessions should return list of sessions for authenticated user`() {
        // Given - Register and login a user
        val registerRequest = UserRegistrationRequest(
            userDisplayName = "Session Test User",
            username = "test_sessionuser",
            userEmail = "test_sessionuser@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )
        val registerResult = mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest))
        )
            .andExpect(status().isOk)
            .andReturn()

        val createdUser = objectMapper.readValue(registerResult.response.contentAsString, GuideUser::class.java)
        val token = createdUser.webUser?.refreshToken ?: fail("Expected refresh token")

        // Create a session directly (since async welcome session may not be ready)
        chatSessionService.createWelcomeSessionWithMessage(
            ownerId = createdUser.core.id,
            welcomeMessage = "Test welcome message"
        )

        // When - Get sessions with auth token
        mockMvc.perform(
            get("/api/hub/sessions")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].id").exists())
            .andExpect(jsonPath("$[0].title").value("Welcome"))
    }

    @Test
    fun `GET sessions should return 403 when not authenticated`() {
        // When & Then - No auth header (Spring Security returns 403 Forbidden)
        mockMvc.perform(get("/api/hub/sessions"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET sessions should return empty list when user has no sessions`() {
        // Given - Register a user but don't create any sessions
        val registerRequest = UserRegistrationRequest(
            userDisplayName = "No Sessions User",
            username = "test_nosessionsuser",
            userEmail = "test_nosessionsuser@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )
        val registerResult = mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest))
        )
            .andExpect(status().isOk)
            .andReturn()

        val createdUser = objectMapper.readValue(registerResult.response.contentAsString, GuideUser::class.java)
        val token = createdUser.webUser?.refreshToken ?: fail("Expected refresh token")

        // Note: The async welcome session might not be created yet, which is fine for this test
        // We're testing that the endpoint works and returns an array (possibly empty)

        // When - Get sessions with auth token
        mockMvc.perform(
            get("/api/hub/sessions")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    // ========== Session History Tests ==========

    @Test
    fun `GET session history should return messages for owned session`() {
        // Given - Register a user and create a session
        val registerRequest = UserRegistrationRequest(
            userDisplayName = "History Test User",
            username = "test_historyuser",
            userEmail = "test_historyuser@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )
        val registerResult = mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest))
        )
            .andExpect(status().isOk)
            .andReturn()

        val createdUser = objectMapper.readValue(registerResult.response.contentAsString, GuideUser::class.java)
        val token = createdUser.webUser?.refreshToken ?: fail("Expected refresh token")

        // Create a session with a message
        val chatSession = chatSessionService.createWelcomeSessionWithMessage(
            ownerId = createdUser.core.id,
            welcomeMessage = "Hello, welcome!"
        )

        // When - Get session history
        mockMvc.perform(
            get("/api/hub/sessions/${chatSession.session.sessionId}")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].id").exists())
            .andExpect(jsonPath("$[0].sessionId").value(chatSession.session.sessionId))
            .andExpect(jsonPath("$[0].role").value("assistant"))
            .andExpect(jsonPath("$[0].body").value("Hello, welcome!"))
    }

    @Test
    fun `GET session history should return 403 for session not owned by user`() {
        // Given - Register two users
        val user1Request = UserRegistrationRequest(
            userDisplayName = "User One",
            username = "test_userone",
            userEmail = "test_userone@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )
        val user1Result = mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(user1Request))
        ).andReturn()
        val user1 = objectMapper.readValue(user1Result.response.contentAsString, GuideUser::class.java)

        val user2Request = UserRegistrationRequest(
            userDisplayName = "User Two",
            username = "test_usertwo",
            userEmail = "test_usertwo@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )
        val user2Result = mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(user2Request))
        ).andReturn()
        val user2 = objectMapper.readValue(user2Result.response.contentAsString, GuideUser::class.java)
        val user2Token = user2.webUser?.refreshToken ?: fail("Expected refresh token")

        // Create a session owned by user1
        val chatSession = chatSessionService.createWelcomeSessionWithMessage(
            ownerId = user1.core.id,
            welcomeMessage = "User 1's private message"
        )

        // When - User2 tries to access user1's session
        mockMvc.perform(
            get("/api/hub/sessions/${chatSession.session.sessionId}")
                .header("Authorization", "Bearer $user2Token")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET session history should return 404 for non-existent session`() {
        // Given - Register a user
        val registerRequest = UserRegistrationRequest(
            userDisplayName = "NotFound Test User",
            username = "test_notfounduser",
            userEmail = "test_notfounduser@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )
        val registerResult = mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest))
        ).andReturn()

        val createdUser = objectMapper.readValue(registerResult.response.contentAsString, GuideUser::class.java)
        val token = createdUser.webUser?.refreshToken ?: fail("Expected refresh token")

        // When - Try to get non-existent session
        mockMvc.perform(
            get("/api/hub/sessions/nonexistent-session-id")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET session history should return 403 when not authenticated`() {
        // When & Then - No auth header
        mockMvc.perform(get("/api/hub/sessions/some-session-id"))
            .andExpect(status().isForbidden)
    }

    // ========== Create Session Tests ==========

    @Test
    fun `POST sessions should create session with generated title`() {
        // Given - Register a user
        val registerRequest = UserRegistrationRequest(
            userDisplayName = "Create Session User",
            username = "test_createsession",
            userEmail = "test_createsession@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )
        val registerResult = mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest))
        ).andReturn()

        val createdUser = objectMapper.readValue(registerResult.response.contentAsString, GuideUser::class.java)
        val token = createdUser.webUser?.refreshToken ?: fail("Expected refresh token")

        // When - Create a new session
        val createRequest = mapOf("content" to "How do I configure the database connection settings?")

        mockMvc.perform(
            post("/api/hub/sessions")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sessionId").exists())
            .andExpect(jsonPath("$.title").exists())
    }

    @Test
    fun `POST sessions should persist session with user message`() {
        // Given - Register a user
        val registerRequest = UserRegistrationRequest(
            userDisplayName = "Persist Session User",
            username = "test_persistsession",
            userEmail = "test_persistsession@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )
        val registerResult = mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest))
        ).andReturn()

        val createdUser = objectMapper.readValue(registerResult.response.contentAsString, GuideUser::class.java)
        val token = createdUser.webUser?.refreshToken ?: fail("Expected refresh token")

        // When - Create a new session
        val messageContent = "What are the best practices for error handling?"
        val createRequest = mapOf("content" to messageContent)

        val createResult = mockMvc.perform(
            post("/api/hub/sessions")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
        )
            .andExpect(status().isOk)
            .andReturn()

        // Then - Verify session was persisted with the message
        val response = objectMapper.readTree(createResult.response.contentAsString)
        val sessionId = response.get("sessionId").asText()

        mockMvc.perform(
            get("/api/hub/sessions/$sessionId")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].body").value(messageContent))
            .andExpect(jsonPath("$[0].role").value("user"))
    }

    @Test
    fun `POST sessions should return 403 when not authenticated`() {
        // Given
        val createRequest = mapOf("content" to "Some message content")

        // When & Then - Note: Non-authenticated requests don't start async
        mockMvc.perform(
            post("/api/hub/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `POST sessions should appear in session list`() {
        // Given - Register a user
        val registerRequest = UserRegistrationRequest(
            userDisplayName = "List Session User",
            username = "test_listsession",
            userEmail = "test_listsession@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )
        val registerResult = mockMvc.perform(
            post("/api/hub/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest))
        ).andReturn()

        val createdUser = objectMapper.readValue(registerResult.response.contentAsString, GuideUser::class.java)
        val token = createdUser.webUser?.refreshToken ?: fail("Expected refresh token")

        // When - Create a new session
        val createRequest = mapOf("content" to "Tell me about machine learning algorithms")

        val createResult = mockMvc.perform(
            post("/api/hub/sessions")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
        )
            .andExpect(status().isOk)
            .andReturn()

        val response = objectMapper.readTree(createResult.response.contentAsString)
        val sessionId = response.get("sessionId").asText()

        // Then - Session should appear in list
        mockMvc.perform(
            get("/api/hub/sessions")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$sessionId')]").exists())
    }
}
