package com.embabel.hub

import com.embabel.guide.chat.model.DeliveredMessage
import com.embabel.guide.chat.service.ChatSessionService
import com.embabel.guide.domain.GuideUser
import com.embabel.guide.domain.GuideUserService
import kotlinx.coroutines.runBlocking
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
    data class CreateSessionRequest(val content: String)
    data class CreateSessionResponse(val sessionId: String, val title: String?)

    @PostMapping("/sessions")
    fun createSession(
        @RequestBody request: CreateSessionRequest,
        authentication: Authentication?
    ): CreateSessionResponse {
        val webUserId = authentication?.principal as? String
            ?: throw UnauthorizedException()
        val guideUser = guideUserService.findByWebUserId(webUserId)
            .orElseThrow { UnauthorizedException() }

        val chatSession = runBlocking {
            chatSessionService.createSessionFromContent(
                ownerId = guideUser.core.id,
                content = request.content
            )
        }
        return CreateSessionResponse(chatSession.session.sessionId, chatSession.session.title)
    }

    @GetMapping("/sessions")
    fun listSessions(authentication: Authentication?): List<SessionSummary> {
        val webUserId = authentication?.principal as? String
            ?: throw UnauthorizedException()
        val guideUser = guideUserService.findByWebUserId(webUserId)
            .orElseThrow { UnauthorizedException() }
        return chatSessionService.findByOwnerIdByRecentActivity(guideUser.core.id)
            .map { SessionSummary(it.session.sessionId, it.session.title) }
    }

    @GetMapping("/sessions/{sessionId}")
    fun getSessionHistory(
        @PathVariable sessionId: String,
        authentication: Authentication?
    ): List<DeliveredMessage> {
        val webUserId = authentication?.principal as? String
            ?: throw UnauthorizedException()
        val guideUser = guideUserService.findByWebUserId(webUserId)
            .orElseThrow { UnauthorizedException() }

        val chatSession = chatSessionService.findBySessionId(sessionId)
            .orElseThrow { NotFoundException("Session not found") }

        // Security check: only owner can view session
        if (chatSession.owner.core.id != guideUser.core.id) {
            throw ForbiddenException("Access denied")
        }

        return chatSession.messages.map { DeliveredMessage.createFrom(it, sessionId) }
    }
}
