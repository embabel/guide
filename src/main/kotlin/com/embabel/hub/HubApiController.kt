package com.embabel.hub

import com.embabel.guide.domain.GuideUser
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/hub")
class HubApiController(private val hubService: HubService) {

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
}