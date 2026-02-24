package com.embabel.hub

import jakarta.servlet.http.HttpServletRequest
import org.drivine.DrivineException
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
@Order(1)
class HubExceptionHandler {

    @ExceptionHandler(RegistrationException::class)
    fun handleRegistrationException(ex: RegistrationException, request: HttpServletRequest) =
        buildResponse(HttpStatus.BAD_REQUEST, ex.message, request)

    @ExceptionHandler(LoginException::class)
    fun handleLoginException(ex: LoginException, request: HttpServletRequest) =
        buildResponse(HttpStatus.UNAUTHORIZED, ex.message, request)

    @ExceptionHandler(ChangePasswordException::class)
    fun handleChangePasswordException(ex: ChangePasswordException, request: HttpServletRequest) =
        buildResponse(HttpStatus.BAD_REQUEST, ex.message, request)

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorizedException(ex: UnauthorizedException, request: HttpServletRequest) =
        buildResponse(HttpStatus.UNAUTHORIZED, ex.message, request)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFoundException(ex: NotFoundException, request: HttpServletRequest) =
        buildResponse(HttpStatus.NOT_FOUND, ex.message, request)

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbiddenException(ex: ForbiddenException, request: HttpServletRequest) =
        buildResponse(HttpStatus.FORBIDDEN, ex.message, request)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException, request: HttpServletRequest) =
        buildResponse(HttpStatus.BAD_REQUEST, ex.message, request)

    @ExceptionHandler(DrivineException::class)
    fun handleDrivineException(ex: DrivineException, request: HttpServletRequest): ResponseEntity<StandardErrorResponse> {
        val rootMessage = ex.rootCause?.message ?: ex.message ?: ""
        if (rootMessage.contains("ConstraintValidationFailed") || rootMessage.contains("already exists")) {
            val message = when {
                rootMessage.contains("userName") -> "Username is already taken"
                rootMessage.contains("userEmail") -> "An account with this email already exists"
                else -> "A uniqueness constraint was violated"
            }
            return buildResponse(HttpStatus.CONFLICT, message, request)
        }
        throw ex
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(ex: HttpMessageNotReadableException, request: HttpServletRequest) =
        buildResponse(HttpStatus.BAD_REQUEST, "Invalid request body", request)

    private fun buildResponse(
        status: HttpStatus,
        message: String?,
        request: HttpServletRequest
    ): ResponseEntity<StandardErrorResponse> {
        val errorResponse = StandardErrorResponse(
            timestamp = Instant.now(),
            status = status.value(),
            error = status.reasonPhrase,
            message = message ?: "An error occurred",
            path = request.requestURI
        )
        return ResponseEntity.status(status).body(errorResponse)
    }
}