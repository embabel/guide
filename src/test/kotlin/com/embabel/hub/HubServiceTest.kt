package com.embabel.hub

import com.embabel.guide.TestApplicationContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.annotation.Rollback
import org.springframework.test.context.ContextConfiguration

@SpringBootTest
@ContextConfiguration(classes = [TestApplicationContext::class])
@Rollback(false)
class HubServiceTest {

    @Autowired
    lateinit var service: HubService

    @Autowired
    lateinit var jwtTokenService: JwtTokenService

    private val passwordEncoder = BCryptPasswordEncoder()

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
        assertNotNull(result)
        assertNotNull(result.id)
        assertEquals("John Doe", result.displayName)
        assertEquals("johndoe", result.username)
        assertEquals("john.doe@example.com", result.email)

        // Verify password is hashed (not stored as plain text)
        val webUser = result.webUser
        assertNotNull(webUser)
        assertNotEquals("SecurePassword123!", webUser!!.passwordHash)
        assertTrue(passwordEncoder.matches("SecurePassword123!", webUser.passwordHash))

        // Verify refresh token is a valid JWT
        assertNotNull(webUser.refreshToken)
        val userId = jwtTokenService.validateRefreshToken(webUser.refreshToken)
        assertEquals(webUser.userId, userId)
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

}
