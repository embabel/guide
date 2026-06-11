package com.embabel.hub

import com.embabel.guide.chat.service.ChatSessionService
import com.embabel.guide.domain.GuideUser
import com.embabel.guide.domain.GuideUserCache
import com.embabel.guide.domain.GuideUserService
import com.embabel.guide.domain.WebUserData
import com.embabel.chat.store.util.UUIDv7
import com.embabel.hub.email.EmailService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

@Service
class HubService(
    private val guideUserService: GuideUserService,
    private val jwtTokenService: JwtTokenService,
    private val welcomeGreeter: WelcomeGreeter,
    private val chatSessionService: ChatSessionService,
    private val guideUserCache: GuideUserCache,
    private val emailService: EmailService,
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

        // Check for duplicate username
        if (guideUserService.findByWebUserName(request.username).isPresent) {
            throw RegistrationException("Username '${request.username}' is already taken")
        }

        // Check for duplicate email
        if (guideUserService.findByWebUserEmail(request.userEmail).isPresent) {
            throw RegistrationException("An account with this email already exists")
        }

        // Generate unique user ID using UUIDv7 (time-ordered)
        val userId = UUIDv7.generateString()

        // Hash the password with BCrypt (includes automatic salt generation)
        val passwordHash = passwordEncoder.encode(request.password)

        // Generate JWT refresh token with built-in expiry
        val refreshToken = jwtTokenService.generateRefreshToken(userId)

        // Create the WebUserData
        val webUser = WebUserData(
            userId,
            request.userDisplayName,
            request.username,
            request.userEmail,
            passwordHash,
            refreshToken
        )

        // Save the user through GuideUserService
        val savedUser = guideUserService.saveFromWebUser(webUser)

        // Send verification email (non-blocking, failure doesn't prevent registration)
        emailService.sendVerificationEmail(webUser.id, request.userEmail)

        return savedUser
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

        val guideUser = guideUserService.findByWebUserName(request.username).orElseThrow {
            LoginException("Invalid username or password")
        }

        val webUser = guideUser.webUser
            ?: throw LoginException("Invalid username or password")

        // OAuth-only accounts have no password and must not be reachable via password login —
        // reject explicitly rather than relying on the encoder returning false for a null hash.
        // Generic message to avoid revealing that the account exists / is OAuth-only.
        val passwordHash = webUser.passwordHash
            ?: throw LoginException("Invalid username or password")

        if (!passwordEncoder.matches(request.password, passwordHash)) {
            throw LoginException("Invalid username or password")
        }

        // Welcome is deferred to first BYOK key set (see IntegrationsController.fireWelcome)
        // so the AI greeting has an LLM available to generate the message.

        // Generate a fresh token on login for accurate expiration
        val token = jwtTokenService.generateRefreshToken(webUser.id)
        val expiresAt = java.time.Instant.now().plusSeconds(jwtTokenService.tokenExpirationSeconds)

        return LoginResponse(
            token = token,
            expiresAt = expiresAt,
            userId = webUser.id,
            username = webUser.userName,
            displayName = webUser.displayName,
            email = webUser.userEmail ?: "",
            persona = guideUser.persona.id,
            emailVerified = webUser.emailVerified,
            hasPassword = webUser.passwordHash != null,
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
        return guideUserService.updatePersona(user.core.id, persona)
            .also { guideUserCache.invalidate(userId) }
    }

    /**
     * Sets or changes the password for a user.
     *
     * For an account that already has a password, the current password is verified and the new
     * password must differ. For an OAuth-only account (no password yet), this sets the first
     * password — no current password is required, since the active authenticated session is the
     * authorization.
     *
     * @param userId the user's ID (from authentication)
     * @param request the change password request
     * @throws ChangePasswordException if validation fails
     */
    fun changePassword(userId: String, request: ChangePasswordRequest) {
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

        // Changing an existing password requires verifying the current one; setting the first
        // password (OAuth-only account) does not.
        if (webUser.passwordHash != null) {
            val currentPassword = request.currentPassword
            if (currentPassword.isNullOrBlank()) {
                throw ChangePasswordException("Current password is required")
            }
            if (!passwordEncoder.matches(currentPassword, webUser.passwordHash)) {
                throw ChangePasswordException("Current password is incorrect")
            }
            if (passwordEncoder.matches(request.newPassword, webUser.passwordHash)) {
                throw ChangePasswordException("New password must be different from current password")
            }
        }

        webUser.passwordHash = passwordEncoder.encode(request.newPassword)
        guideUserService.saveUser(guideUser)
        guideUserCache.invalidate(userId)
    }

}
