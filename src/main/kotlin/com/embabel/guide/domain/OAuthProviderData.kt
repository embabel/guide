package com.embabel.guide.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.drivine.annotation.NodeFragment
import org.drivine.annotation.NodeId

/**
 * Node fragment representing a linked OAuth provider identity (e.g. Google, GitHub).
 * A GuideUser may have zero or more of these via HAS_OAUTH_PROVIDER relationships.
 */
@NodeFragment(labels = ["OAuthProvider"])
@JsonIgnoreProperties(ignoreUnknown = true)
data class OAuthProviderData(
    @NodeId
    var id: String,
    var provider: String,
    var providerUserId: String,
    var email: String?,
    var displayName: String?,
    var avatarUrl: String?,
)
