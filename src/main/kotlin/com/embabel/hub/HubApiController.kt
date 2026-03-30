package com.embabel.hub

import com.embabel.guide.chat.model.DeliveredMessage
import com.embabel.guide.chat.service.ChatSessionService
import com.embabel.guide.domain.GuideUser
import com.embabel.guide.domain.GuideUserService
import io.jsonwebtoken.JwtException
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import java.time.Instant
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/hub")
class HubApiController(
    private val hubService: HubService,
    private val personaService: PersonaService,
    private val guideUserService: GuideUserService,
    private val chatSessionService: ChatSessionService,
    private val jwtTokenService: JwtTokenService
) {

    @PostMapping("/register")
    fun registerUser(@RequestBody request: UserRegistrationRequest): GuideUser {
        return hubService.registerUser(request)
    }

    @PostMapping("/login")
    fun loginUser(@RequestBody request: UserLoginRequest): LoginResponse {
        return hubService.loginUser(request)
    }

    data class RefreshTokenRequest(val token: String)
    data class RefreshTokenResponse(val token: String, val expiresAt: Instant)

    @PostMapping("/refresh")
    fun refreshToken(@RequestBody request: RefreshTokenRequest): ResponseEntity<RefreshTokenResponse> {
        return try {
            // Parse the token (even if expired) to get the user ID - signature must still be valid
            val claims = jwtTokenService.parseTokenIgnoringExpiration(request.token)
            val userId = claims.subject

            // Verify the user still exists
            guideUserService.findByWebUserId(userId)
                .orElseThrow { UnauthorizedException("User not found") }

            // Generate a new token
            val newToken = jwtTokenService.generateRefreshToken(userId)
            val expiresAt = Instant.now().plusSeconds(jwtTokenService.tokenExpirationSeconds)

            ResponseEntity.ok(RefreshTokenResponse(newToken, expiresAt))
        } catch (ex: JwtException) {
            throw UnauthorizedException("Invalid token")
        }
    }

    @GetMapping("/personas")
    fun listPersonas(authentication: Authentication?): List<PersonaDto> {
        val userId = authentication?.principal as? String
        return personaService.listPersonasForUser(userId)
    }

    data class CreatePersonaRequest(
        val name: String,
        val prompt: String,
        val voice: String? = null,
        val effects: List<AudioEffect>? = null,
    )

    @PostMapping("/personas")
    fun createPersona(
        @RequestBody request: CreatePersonaRequest,
        authentication: Authentication?,
    ): PersonaDto {
        val userId = authentication?.principal as? String ?: throw UnauthorizedException()
        return personaService.createPersona(userId, request.name, request.prompt, request.voice, request.effects)
    }

    @PostMapping("/personas/{id}/copy")
    fun copyPersona(
        @PathVariable id: String,
        authentication: Authentication?,
    ): PersonaDto {
        val userId = authentication?.principal as? String ?: throw UnauthorizedException()
        return personaService.copyPersona(userId, id)
    }

    @DeleteMapping("/personas/{id}")
    fun deletePersona(
        @PathVariable id: String,
        authentication: Authentication?,
    ) {
        val userId = authentication?.principal as? String ?: throw UnauthorizedException()
        personaService.deletePersona(userId, id)
    }

    data class UpdatePersonaRequest(val persona: String)

    @PutMapping("/persona/mine")
    fun updateMyPersona(
        @RequestBody request: UpdatePersonaRequest,
        authentication: Authentication?
    ) {
        val userId = authentication?.principal as? String
            ?: throw UnauthorizedException()
        hubService.updatePersona(userId, request.persona)
    }

    @PutMapping("/password")
    fun changePassword(
        @RequestBody request: ChangePasswordRequest,
        authentication: Authentication?
    ) {
        val userId = authentication?.principal as? String
            ?: throw UnauthorizedException()
        hubService.changePassword(userId, request)
    }

    data class SessionSummary(val id: String, val title: String?)

    @GetMapping("/sessions")
    fun listSessions(authentication: Authentication?): List<SessionSummary> {
        val guideUser = getAuthenticatedGuideUser(authentication)
            ?: return emptyList()  // Anonymous users can't list sessions
        return chatSessionService.findByOwnerIdByRecentActivity(guideUser.core.id)
            .map { SessionSummary(it.session.sessionId, it.session.title) }
    }

    @GetMapping("/sessions/{sessionId}")
    fun getSessionHistory(
        @PathVariable sessionId: String,
        authentication: Authentication?
    ): List<DeliveredMessage> {
        val guideUser = getAuthenticatedGuideUser(authentication)
            ?: throw ForbiddenException("Anonymous users cannot access session history")

        val chatSession = chatSessionService.findBySessionId(sessionId)
            .orElseThrow { NotFoundException("Session not found") }

        // Security check: only owner can view session
        if (chatSession.owner.id != guideUser.core.id) {
            throw ForbiddenException("Access denied")
        }

        return chatSession.messages.map { DeliveredMessage.createFrom(it, sessionId, chatSession.session.title) }
    }

    /**
     * Gets the GuideUser for authenticated users only.
     * Returns null if unauthenticated or user not found.
     */
    private fun getAuthenticatedGuideUser(authentication: Authentication?): GuideUser? {
        val webUserId = authentication?.principal as? String ?: return null
        return guideUserService.findByWebUserId(webUserId).orElse(null)
    }
}
