package com.embabel.hub

/**
 * Response data for successful login.
 *
 * @property token The JWT access/refresh token
 * @property userId The user's unique ID
 * @property username The username
 * @property displayName The user's display name
 * @property email The user's email address
 */
data class LoginResponse(
    val token: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val email: String
)