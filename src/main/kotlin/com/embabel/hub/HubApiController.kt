package com.embabel.hub

import com.embabel.guide.chat.model.DeliveredMessage
import com.embabel.guide.chat.service.ChatSessionService
import com.embabel.guide.domain.GuideUser
import com.embabel.guide.domain.GuideUserService
import org.springframework.security.core.Authentication
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
    private val chatSessionService: ChatSessionService
) {

    @PostMapping("/register")
    fun registerUser(@RequestBody request: UserRegistrationRequest): GuideUser {
        return hubService.registerUser(request)
    }

    @PostMapping("/login")
    fun loginUser(@RequestBody request: UserLoginRequest): LoginResponse {
        return hubService.loginUser(request)
    }

    @GetMapping("/personas")
    fun listPersonas(): List<PersonaService.Persona> {
        return personaService.listPersonas()
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
