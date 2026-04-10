package com.embabel.hub

import java.time.Instant

/**
 * Response data for successful login.
 *
 * @property token The JWT access/refresh token
 * @property expiresAt When the token expires (ISO 8601 format)
 * @property userId The user's unique ID
 * @property username The username
 * @property displayName The user's display name
 * @property email The user's email address
 * @property persona The user's selected persona ID
 */
data class LoginResponse(
    val token: String,
    val expiresAt: Instant,
    val userId: String,
    val username: String,
    val displayName: String,
    val email: String,
    val persona: String,
    val emailVerified: Boolean = false,
)