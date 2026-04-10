package com.embabel.guide.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.drivine.annotation.NodeFragment
import org.drivine.annotation.NodeId

/**
 * Node fragment representing a WebUser in the graph.
 */
@NodeFragment(labels = ["WebUser"])
@JsonIgnoreProperties(ignoreUnknown = true)
open class WebUserData(
    @NodeId
    var id: String,
    var displayName: String,
    var userName: String,
    var userEmail: String?,
    var passwordHash: String?,
    var refreshToken: String?,
    var emailVerified: Boolean = false,
    var emailVerificationToken: String? = null,
    var emailVerificationExpiry: java.time.Instant? = null,
) {
    override fun toString(): String =
        "WebUserData{userId='$id', userDisplayName='$displayName', userUsername='$userName'}"
}
