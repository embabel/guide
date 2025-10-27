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
        if (request.password != request.passwordConfirmation) {
            throw RegistrationException("Password and password confirmation do not match")
        }

        if (request.password.length < 8) {
            throw RegistrationException("Password must be at least 8 characters long")
        }

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

        val guideUser = guideUserService.findByUsername(request.username)
            ?: throw LoginException("Invalid username or password")

        val webUser = guideUser.webUser
            ?: throw LoginException("Invalid username or password")

        if (!passwordEncoder.matches(request.password, webUser.passwordHash)) {
            throw LoginException("Invalid username or password")
        }

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
        if (persona.isBlank()) {
            throw IllegalArgumentException("Persona cannot be blank")
        }
        val user = guideUserService.findByWebUserId(userId).orElseThrow()
        return guideUserService.updatePersona(user.id, persona)
    }

    /**
     * Changes the password for a user.
     *
     * Validates the current password, ensures the new password meets requirements,
     * and updates the password hash.
     *
     * @param userId the user's ID (from authentication)
     * @param request the change password request
     * @throws ChangePasswordException if validation fails
     */
    fun changePassword(userId: String, request: ChangePasswordRequest) {
        if (request.currentPassword.isBlank()) {
            throw ChangePasswordException("Current password is required")
        }
        if (request.newPassword.isBlank()) {
            throw ChangePasswordException("New password is required")
        }

        if (request.newPassword != request.newPasswordConfirmation) {
            throw ChangePasswordException("New password and confirmation do not match")
        }

        if (request.newPassword.length < 8) {
            throw ChangePasswordException("New password must be at least 8 characters long")
        }

        val guideUser = guideUserService.findByWebUserId(userId)
            .orElseThrow { ChangePasswordException("User not found") }

        val webUser = guideUser.webUser
            ?: throw ChangePasswordException("User not found")

        if (!passwordEncoder.matches(request.currentPassword, webUser.passwordHash)) {
            throw ChangePasswordException("Current password is incorrect")
        }

        // Check that new password is different from current
        if (passwordEncoder.matches(request.newPassword, webUser.passwordHash)) {
            throw ChangePasswordException("New password must be different from current password")
        }

        val newPasswordHash = passwordEncoder.encode(request.newPassword)
        webUser.passwordHash = newPasswordHash

        guideUserService.saveUser(guideUser)
    }

}
