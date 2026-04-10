package com.embabel.hub.email

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/hub/email")
class EmailController(
    private val emailService: EmailService,
) {

    @GetMapping("/verify")
    fun verifyEmail(@RequestParam token: String): ResponseEntity<Map<String, Boolean>> {
        val success = emailService.verifyEmail(token)
        return ResponseEntity.ok(mapOf("verified" to success))
    }

    @PostMapping("/resend-verification")
    fun resendVerification(authentication: Authentication): ResponseEntity<Void> {
        val userId = authentication.principal as String
        emailService.resendVerification(userId)
        return ResponseEntity.ok().build()
    }
}
