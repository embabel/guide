package com.embabel.hub

import com.embabel.guide.chat.model.DeliveredMessage
import com.embabel.guide.chat.service.ThreadService
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
    private val threadService: ThreadService
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

    data class ThreadSummary(val id: String, val title: String?)

    @GetMapping("/threads")
    fun listThreads(authentication: Authentication?): List<ThreadSummary> {
        val webUserId = authentication?.principal as? String
            ?: throw UnauthorizedException()
        val guideUser = guideUserService.findByWebUserId(webUserId)
            .orElseThrow { UnauthorizedException() }
        return threadService.findByOwnerId(guideUser.core.id)
            .map { ThreadSummary(it.thread.threadId, it.thread.title) }
    }

    @GetMapping("/threads/{threadId}")
    fun getThreadHistory(
        @PathVariable threadId: String,
        authentication: Authentication?
    ): List<DeliveredMessage> {
        val webUserId = authentication?.principal as? String
            ?: throw UnauthorizedException()
        val guideUser = guideUserService.findByWebUserId(webUserId)
            .orElseThrow { UnauthorizedException() }

        val timeline = threadService.findByThreadId(threadId)
            .orElseThrow { NotFoundException("Thread not found") }

        // Security check: only owner can view thread
        if (timeline.owner.core.id != guideUser.core.id) {
            throw ForbiddenException("Access denied")
        }

        return timeline.messages.map { DeliveredMessage.createFrom(it, threadId) }
    }
}
