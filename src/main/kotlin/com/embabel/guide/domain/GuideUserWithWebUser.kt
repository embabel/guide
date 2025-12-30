package com.embabel.guide.domain

import com.embabel.agent.api.identity.User
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Composed result type for GuideUser queries that include web user information.
 * Following Drivine's composition philosophy rather than ORM relationships.
 * Implements User interface by delegating to the WebUser data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GuideUserWithWebUser(
    @JsonProperty
    var guideUserData: GuideUserData,
    @JsonProperty
    override var webUser: WebUserData
) : HasGuideUserData, HasWebUserData, User {

    override fun guideUserData(): GuideUserData = guideUserData

    // User interface delegation to WebUser
    override val id: String
        get() = webUser.id

    override val displayName: String
        get() = webUser.displayName

    override val username: String
        get() = webUser.userName

    override val email: String?
        get() = webUser.userEmail
}
