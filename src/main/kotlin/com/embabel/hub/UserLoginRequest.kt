package com.embabel.hub

/**
 * Request data for user login.
 *
 * @property username The username
 * @property password The plain text password
 */
data class UserLoginRequest(
    val username: String,
    val password: String
)
