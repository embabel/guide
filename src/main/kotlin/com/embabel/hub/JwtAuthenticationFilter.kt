package com.embabel.hub

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

/**
 * Filter that extracts JWT tokens from HTTP requests and authenticates users.
 *
 * Looks for the token in the Authorization header as "Bearer <token>".
 * If a valid token is found, sets the authentication in the SecurityContext.
 * If the token is invalid or expired, returns a proper error response.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtTokenService: JwtTokenService,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        // SockJS/WebSocket paths handle JWT validation themselves in
        // AnonymousPrincipalHandshakeHandler (token in query param or header).
        // Running the filter here would 401 the /ws/info poll on an expired
        // token and break the SockJS handshake before it can fall back to
        // anonymous.
        val uri = request.requestURI
        return uri == "/ws" || uri.startsWith("/ws/")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = extractTokenFromRequest(request)

        if (token != null) {
            val authResult = authenticateToken(token)
            if (authResult is AuthResult.Error) {
                sendErrorResponse(response, request.requestURI, authResult.code, authResult.message)
                return
            }
        }

        filterChain.doFilter(request, response)
    }

    private sealed class AuthResult {
        data object Success : AuthResult()
        data class Error(val code: String, val message: String) : AuthResult()
    }

    private fun authenticateToken(token: String): AuthResult {
        return try {
            val userId = jwtTokenService.validateRefreshToken(token)
            setAuthentication(userId)
            AuthResult.Success
        } catch (ex: ExpiredJwtException) {
            logger.debug("JWT token expired")
            AuthResult.Error("TOKEN_EXPIRED", "Your session has expired. Please sign in again.")
        } catch (ex: JwtException) {
            logger.debug("JWT validation failed: ${ex.javaClass.simpleName}")
            AuthResult.Error("TOKEN_INVALID", "Your session is invalid. Please sign in again.")
        } catch (ex: Exception) {
            logger.debug("JWT validation failed: ${ex.message}")
            AuthResult.Error("TOKEN_INVALID", "Your session is invalid. Please sign in again.")
        }
    }

    private fun setAuthentication(userId: String) {
        val authentication = UsernamePasswordAuthenticationToken(
            userId,
            null,
            emptyList()
        )
        SecurityContextHolder.getContext().authentication = authentication
    }

    private fun sendErrorResponse(
        response: HttpServletResponse,
        path: String,
        code: String,
        message: String
    ) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val errorResponse = StandardErrorResponse(
            timestamp = Instant.now(),
            status = 401,
            error = "Unauthorized",
            code = code,
            message = message,
            path = path
        )

        response.writer.write(objectMapper.writeValueAsString(errorResponse))
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