package com.embabel.hub

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Filter that extracts JWT tokens from HTTP requests and authenticates users.
 *
 * Looks for the token in the Authorization header as "Bearer <token>".
 * If a valid token is found, sets the authentication in the SecurityContext.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtTokenService: JwtTokenService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val token = extractTokenFromRequest(request)

            if (token != null) {
                // Validate the token and extract user ID
                val userId = jwtTokenService.validateRefreshToken(token)

                // Create authentication with the user ID as the principal
                val authentication = UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    emptyList() // No authorities/roles for now
                )

                // Set the authentication in the security context
                SecurityContextHolder.getContext().authentication = authentication
            }
        } catch (e: Exception) {
            // If token validation fails, just continue without authentication
            // The security configuration will handle unauthorized access
            logger.debug("JWT validation failed: ${e.message}")
        }

        filterChain.doFilter(request, response)
    }

    /**
     * Extracts the JWT token from the Authorization header.
     * Expects format: "Bearer <token>"
     */
    private fun extractTokenFromRequest(request: HttpServletRequest): String? {
        val authHeader = request.getHeader("Authorization")

        return if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authHeader.substring(7) // Remove "Bearer " prefix
        } else {
            null
        }
    }
}