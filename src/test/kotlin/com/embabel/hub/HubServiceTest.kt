package com.embabel.hub

import com.embabel.guide.Neo4jPropertiesInitializer
import com.embabel.guide.domain.GuideUserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
@ImportAutoConfiguration(exclude = [McpClientAutoConfiguration::class])
class HubServiceTest {

    @Autowired
    lateinit var service: HubService

    @Autowired
    lateinit var jwtTokenService: JwtTokenService

    @Autowired
    lateinit var guideUserRepository: GuideUserRepository

    private val passwordEncoder = BCryptPasswordEncoder()

    companion object {
        private const val TEST_PREFIX = "hubtest-"
    }

    @AfterEach
    fun cleanup() {
        guideUserRepository.deleteByUsernameStartingWith(TEST_PREFIX)
    }

    @Test
    fun `registerUser should create a new user successfully`() {
        // Given
        val request = UserRegistrationRequest(
            userDisplayName = "John Doe",
            username = "johndoe",
            userEmail = "john.doe@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )

        // When
        val result = service.registerUser(request)

        // Then
        assertNotNull(result.guideUserData())
        assertNotNull(result.guideUserData().id)
        assertEquals("John Doe", result.displayName)
        assertEquals("johndoe", result.username)
        assertEquals("john.doe@example.com", result.email)

        // Verify password is hashed (not stored as plain text)
        result.webUser?.let { webUser ->
            assertNotEquals("SecurePassword123!", webUser.passwordHash)
            assertTrue(passwordEncoder.matches("SecurePassword123!", webUser.passwordHash))

            // Verify refresh token is a valid JWT
            webUser.refreshToken?.let { refreshToken ->
                val userId = jwtTokenService.validateRefreshToken(refreshToken)
                assertEquals(webUser.id, userId)
            } ?: fail("Expected refresh token to be present")
        } ?: fail("Expected webUser to be present")
    }

    @Test
    fun `registerUser should fail when passwords do not match`() {
        // Given
        val request = UserRegistrationRequest(
            userDisplayName = "Jane Doe",
            username = "janedoe",
            userEmail = "jane.doe@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "DifferentPassword123!"
        )

        // When & Then
        val exception = assertThrows(RegistrationException::class.java) {
            service.registerUser(request)
        }
        assertEquals("Password and password confirmation do not match", exception.message)
    }

    @Test
    fun `registerUser should fail when password is too short`() {
        // Given
        val request = UserRegistrationRequest(
            userDisplayName = "Bob Smith",
            username = "bobsmith",
            userEmail = "bob.smith@example.com",
            password = "Short1!",
            passwordConfirmation = "Short1!"
        )

        // When & Then
        val exception = assertThrows(RegistrationException::class.java) {
            service.registerUser(request)
        }
        assertEquals("Password must be at least 8 characters long", exception.message)
    }

    @Test
    fun `registerUser should fail when username is blank`() {
        // Given
        val request = UserRegistrationRequest(
            userDisplayName = "Alice Jones",
            username = "",
            userEmail = "alice.jones@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )

        // When & Then
        val exception = assertThrows(RegistrationException::class.java) {
            service.registerUser(request)
        }
        assertEquals("Username is required", exception.message)
    }

    @Test
    fun `registerUser should fail when email is blank`() {
        // Given
        val request = UserRegistrationRequest(
            userDisplayName = "Charlie Brown",
            username = "charliebrown",
            userEmail = "",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )

        // When & Then
        val exception = assertThrows(RegistrationException::class.java) {
            service.registerUser(request)
        }
        assertEquals("Email is required", exception.message)
    }

    @Test
    fun `registerUser should fail when display name is blank`() {
        // Given
        val request = UserRegistrationRequest(
            userDisplayName = "",
            username = "davidsmith",
            userEmail = "david.smith@example.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )

        // When & Then
        val exception = assertThrows(RegistrationException::class.java) {
            service.registerUser(request)
        }
        assertEquals("Display name is required", exception.message)
    }

    @Test
    fun `updatePersona should persist persona change for web user`() {
        // Given: Register a web user (same as frontend signup)
        val username = "${TEST_PREFIX}persona-${System.nanoTime()}"
        val request = UserRegistrationRequest(
            userDisplayName = "Persona Test User",
            username = username,
            userEmail = "$username@test.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )
        val registeredUser = service.registerUser(request)
        val webUserId = registeredUser.webUser!!.id

        // Verify initial persona is null
        val before = guideUserRepository.findByWebUserId(webUserId).orElseThrow()
        assertNull(before.core.persona)

        // When: Update persona via the same path the frontend uses
        val updatedUser = service.updatePersona(webUserId, "expert")

        // Then: The returned user has the new persona
        assertEquals("expert", updatedUser.core.persona)

        // And: Re-reading from the DB also shows the new persona
        val reloaded = guideUserRepository.findByWebUserId(webUserId).orElseThrow()
        assertEquals("expert", reloaded.core.persona,
            "Persona should be persisted in Neo4j and visible on re-read")
    }

    @Test
    fun `updatePersona should allow changing persona multiple times`() {
        // Given: Register a web user
        val username = "${TEST_PREFIX}multi-persona-${System.nanoTime()}"
        val request = UserRegistrationRequest(
            userDisplayName = "Multi Persona User",
            username = username,
            userEmail = "$username@test.com",
            password = "SecurePassword123!",
            passwordConfirmation = "SecurePassword123!"
        )
        val registeredUser = service.registerUser(request)
        val webUserId = registeredUser.webUser!!.id

        // When: Update persona twice
        service.updatePersona(webUserId, "expert")
        service.updatePersona(webUserId, "adaptive")

        // Then: The final value sticks
        val reloaded = guideUserRepository.findByWebUserId(webUserId).orElseThrow()
        assertEquals("adaptive", reloaded.core.persona)
    }

}
