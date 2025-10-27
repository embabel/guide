package com.embabel.hub

/**
 * Request data for changing a user's password.
 *
 * @property currentPassword The user's current password (for verification)
 * @property newPassword The new password
 * @property newPasswordConfirmation The new password confirmation (must match newPassword)
 */
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
    val newPasswordConfirmation: String
)