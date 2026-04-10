package com.embabel.hub.email

import com.embabel.guide.domain.GuideUserService
import com.sendgrid.Method
import com.sendgrid.Request
import com.sendgrid.SendGrid
import com.sendgrid.helpers.mail.Mail
import com.sendgrid.helpers.mail.objects.Content
import com.sendgrid.helpers.mail.objects.Email
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class EmailService(
    private val guideUserService: GuideUserService,
    @Value("\${guide.email.sendgrid-api-key:}") private val sendGridApiKey: String,
    @Value("\${guide.email.from-email:noreply@embabel.com}") private val fromEmail: String,
    @Value("\${guide.email.frontend-url:http://localhost:3000}") private val frontendUrl: String,
) {

    private val logger = LoggerFactory.getLogger(EmailService::class.java)

    fun sendVerificationEmail(webUserId: String, email: String?) {
        if (email.isNullOrBlank()) return
        if (sendGridApiKey.isBlank()) {
            logger.warn("SendGrid API key not configured — skipping verification email for {}", webUserId)
            return
        }

        val token = UUID.randomUUID().toString()
        val expiry = Instant.now().plus(24, ChronoUnit.HOURS)

        // Store verification token on the user
        val guideUser = guideUserService.findByWebUserId(webUserId).orElse(null) ?: return
        val webUser = guideUser.webUser ?: return
        webUser.emailVerificationToken = token
        webUser.emailVerificationExpiry = expiry
        guideUserService.saveUser(guideUser)

        // Send email via SendGrid
        val verifyUrl = "$frontendUrl/auth/verify-email?token=$token"
        val subject = "Verify your Embabel email"
        val body = """
            <html>
            <body style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                <h2>Welcome to Embabel!</h2>
                <p>Click the button below to verify your email address.</p>
                <p style="margin: 24px 0;">
                    <a href="$verifyUrl"
                       style="background: #4F46E5; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px;">
                        Verify Email
                    </a>
                </p>
                <p style="color: #666; font-size: 14px;">
                    Or copy this link: <a href="$verifyUrl">$verifyUrl</a>
                </p>
                <p style="color: #999; font-size: 12px;">This link expires in 24 hours.</p>
            </body>
            </html>
        """.trimIndent()

        sendEmail(email, subject, body)
    }

    fun verifyEmail(token: String): Boolean {
        val guideUser = guideUserService.findByEmailVerificationToken(token).orElse(null)
            ?: return false
        val webUser = guideUser.webUser ?: return false

        val expiry = webUser.emailVerificationExpiry
        if (expiry != null && Instant.now().isAfter(expiry)) {
            logger.info("Verification token expired for user {}", webUser.id)
            return false
        }

        webUser.emailVerified = true
        webUser.emailVerificationToken = null
        webUser.emailVerificationExpiry = null
        guideUserService.saveUser(guideUser)
        logger.info("Email verified for user {}", webUser.id)
        return true
    }

    fun resendVerification(webUserId: String) {
        val guideUser = guideUserService.findByWebUserId(webUserId).orElseThrow {
            IllegalArgumentException("User not found: $webUserId")
        }
        val webUser = guideUser.webUser
            ?: throw IllegalArgumentException("Not a web user: $webUserId")

        if (webUser.emailVerified) return

        sendVerificationEmail(webUserId, webUser.userEmail)
    }

    private fun sendEmail(to: String, subject: String, htmlBody: String) {
        try {
            val from = Email(fromEmail, "Embabel")
            val toEmail = Email(to)
            val content = Content("text/html", htmlBody)
            val mail = Mail(from, subject, toEmail, content)

            val sg = SendGrid(sendGridApiKey)
            val request = Request().apply {
                method = Method.POST
                endpoint = "mail/send"
                body = mail.build()
            }
            val response = sg.api(request)
            if (response.statusCode in 200..299) {
                logger.info("Verification email sent to {}", to)
            } else {
                logger.error("SendGrid returned {} for {}: {}", response.statusCode, to, response.body)
            }
        } catch (e: Exception) {
            logger.error("Failed to send verification email to {}: {}", to, e.message, e)
        }
    }
}
