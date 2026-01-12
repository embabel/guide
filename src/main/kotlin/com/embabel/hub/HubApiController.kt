package com.embabel.hub

import com.embabel.guide.domain.GuideUser
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/hub")
class HubApiController(
    private val hubService: HubService,
    private val personaService: PersonaService
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
}
