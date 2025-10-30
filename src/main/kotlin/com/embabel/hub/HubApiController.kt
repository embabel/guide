package com.embabel.hub

import com.embabel.guide.domain.GuideUser
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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

    data class ErrorResponse(val error: String)

    @PostMapping("/register")
    fun registerUser(@RequestBody request: UserRegistrationRequest): ResponseEntity<*> {
        return try {
            val user = hubService.registerUser(request)
            ResponseEntity.ok(user)
        } catch (e: RegistrationException) {
            ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse(e.message ?: "Registration failed"))
        }
    }

    @PostMapping("/login")
    fun loginUser(@RequestBody request: UserLoginRequest): ResponseEntity<*> {
        return try {
            val loginResponse = hubService.loginUser(request)
            ResponseEntity.ok(loginResponse)
        } catch (e: LoginException) {
            ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse(e.message ?: "Login failed"))
        }
    }

    @GetMapping("/personas")
    fun listPersonas(): ResponseEntity<List<PersonaService.Persona>> {
        val personas = personaService.listPersonas()
        return ResponseEntity.ok(personas)
    }

    data class UpdatePersonaRequest(val persona: String)

    @PutMapping("/persona/mine")
    fun updateMyPersona(
        @RequestBody request: UpdatePersonaRequest,
        authentication: Authentication?
    ): ResponseEntity<Void> {
        return try {
            // Get user ID from authentication
            val userId = authentication?.principal as? String
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

            // Update the persona
            hubService.updatePersona(userId, request.persona)
            ResponseEntity.ok().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }
    }
}