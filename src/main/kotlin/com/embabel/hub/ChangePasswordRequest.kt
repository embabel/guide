package com.embabel.hub

/**
 * Request data for setting or changing a user's password.
 *
 * @property currentPassword The user's current password (for verification). Null/absent when the
 *   account has no password yet — i.e. an OAuth-only user setting one for the first time.
 * @property newPassword The new password
 * @property newPasswordConfirmation The new password confirmation (must match newPassword)
 */
data class ChangePasswordRequest(
    val currentPassword: String? = null,
    val newPassword: String,
    val newPasswordConfirmation: String
)