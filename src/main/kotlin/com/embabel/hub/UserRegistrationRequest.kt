package com.embabel.hub

/**
 * Request data for user registration.
 *
 * @property userDisplayName The user's display name
 * @property username The unique username
 * @property userEmail The user's email address
 * @property password The plain text password (to be hashed)
 * @property passwordConfirmation The password confirmation (must match password)
 */
data class UserRegistrationRequest(
    val userDisplayName: String,
    val username: String,
    val userEmail: String,
    val password: String,
    val passwordConfirmation: String
)