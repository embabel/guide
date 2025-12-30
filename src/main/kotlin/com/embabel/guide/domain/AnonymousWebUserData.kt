package com.embabel.guide.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Data representation for anonymous web users.
 * Extends WebUserData to add the Anonymous label distinction.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class AnonymousWebUserData(
    userId: String,
    userDisplayName: String,
    userUsername: String,
    userEmail: String?,
    passwordHash: String?,
    refreshToken: String?
) : WebUserData(userId, userDisplayName, userUsername, userEmail, passwordHash, refreshToken) {

    override fun toString(): String =
        "AnonymousWebUserData{userId='$id', userDisplayName='$displayName', userUsername='$userName'}"
}
