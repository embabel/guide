package com.embabel.guide.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Simple data representation of WebUser properties for Drivine composition.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
open class WebUserData(
    var id: String,
    var displayName: String,
    var userName: String,
    var userEmail: String?,
    var passwordHash: String?,
    var refreshToken: String?
) {
    override fun toString(): String =
        "WebUserData{userId='$id', userDisplayName='$displayName', userUsername='$userName'}"
}
