package com.embabel.hub

import com.embabel.guide.domain.GuideUser
import com.embabel.guide.domain.GuideUserService
import com.embabel.guide.domain.WebUser
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.util.*

@Service
class HubService(
    private val guideUserService: GuideUserService,
    private val jwtTokenService: JwtTokenService
) {

    private val passwordEncoder = BCryptPasswordEncoder()

    /**
     * Registers a new web user.
     *
     * Validates the registration request, hashes the password with salt using BCrypt,
     * generates a JWT refresh token with expiry, and stores the user.
     *
     * @param request The user registration request
     * @return The created GuideUser
     * @throws RegistrationException if validation fails or registration cannot be completed
     */
    fun registerUser(request: UserRegistrationRequest): GuideUser {
        // Validate password confirmation
        if (request.password != request.passwordConfirmation) {
            throw RegistrationException("Password and password confirmation do not match")
        }

        // Validate password strength (minimum requirements)
        if (request.password.length < 8) {
            throw RegistrationException("Password must be at least 8 characters long")
        }

        // Validate required fields
        if (request.username.isBlank()) {
            throw RegistrationException("Username is required")
        }
        if (request.userEmail.isBlank()) {
            throw RegistrationException("Email is required")
        }
        if (request.userDisplayName.isBlank()) {
            throw RegistrationException("Display name is required")
        }

        // Generate unique user ID
        val userId = UUID.randomUUID().toString()

        // Hash the password with BCrypt (includes automatic salt generation)
        val passwordHash = passwordEncoder.encode(request.password)

        // Generate JWT refresh token with built-in expiry
        val refreshToken = jwtTokenService.generateRefreshToken(userId)

        // Create the WebUser
        val webUser = WebUser(
            userId,
            request.userDisplayName,
            request.username,
            request.userEmail,
            passwordHash,
            refreshToken
        )

        // Save the user through GuideUserService
        return guideUserService.saveFromWebUser(webUser)
    }

    /**
     * Authenticates a user with username and password.
     *
     * Validates the credentials and returns a login response with a JWT token.
     *
     * @param request The login request with username and password
     * @return LoginResponse containing the JWT token and user information
     * @throws LoginException if the credentials are invalid
     */
    fun loginUser(request: UserLoginRequest): LoginResponse {
        // Validate required fields
        if (request.username.isBlank()) {
            throw LoginException("Username is required")
        }
        if (request.password.isBlank()) {
            throw LoginException("Password is required")
        }

        // Find the user by username
        val guideUser = guideUserService.findByUsername(request.username)
            ?: throw LoginException("Invalid username or password")

        // Get the WebUser
        val webUser = guideUser.webUser
            ?: throw LoginException("Invalid username or password")

        // Verify the password
        if (!passwordEncoder.matches(request.password, webUser.passwordHash)) {
            throw LoginException("Invalid username or password")
        }

        // Return the login response with the refresh token
        return LoginResponse(
            token = webUser.refreshToken,
            userId = webUser.userId,
            username = webUser.username,
            displayName = webUser.displayName,
            email = webUser.email ?: "",
            persona = guideUser.persona()
        )
    }

    /**
     * Updates the persona preference for a user.
     *
     * @param userId the user's ID (from authentication)
     * @param persona the persona name to set
     * @return the updated GuideUser
     */
    fun updatePersona(userId: String, persona: String): GuideUser {
        // Validate persona is not blank
        if (persona.isBlank()) {
            throw IllegalArgumentException("Persona cannot be blank")
        }
        val user = guideUserService.findByWebUserId(userId).orElseThrow()
        return guideUserService.updatePersona(user.id, persona)
    }

}
