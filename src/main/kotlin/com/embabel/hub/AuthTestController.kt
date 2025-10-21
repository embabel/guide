package com.embabel.hub

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Test controller to verify JWT authentication is working.
 * This can be removed in production.
 */
@RestController
@RequestMapping("/api/auth")
class AuthTestController {

    data class AuthInfo(
        val authenticated: Boolean,
        val userId: String?,
        val principal: String?
    )

    @GetMapping("/me")
    fun getCurrentUser(authentication: Authentication?): ResponseEntity<AuthInfo> {
        return ResponseEntity.ok(
            AuthInfo(
                authenticated = authentication?.isAuthenticated ?: false,
                userId = authentication?.principal as? String,
                principal = authentication?.principal?.toString()
            )
        )
    }
}