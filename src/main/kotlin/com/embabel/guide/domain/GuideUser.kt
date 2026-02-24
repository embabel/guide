package com.embabel.guide.domain

import com.embabel.agent.api.identity.User
import org.drivine.annotation.Direction
import org.drivine.annotation.GraphRelationship
import org.drivine.annotation.GraphView
import org.drivine.annotation.Root

/**
 * Unified GraphView for GuideUser with optional identity sources.
 * A GuideUser may be linked to a WebUser, DiscordUserInfo, both, or neither
 * (e.g., when using spring shell).
 */
@GraphView
data class GuideUser(
    @Root
    val core: GuideUserData,

    @GraphRelationship(type = "IS_WEB_USER", direction = Direction.OUTGOING)
    val webUser: WebUserData? = null,

    @GraphRelationship(type = "IS_DISCORD_USER", direction = Direction.OUTGOING)
    val discordUserInfo: DiscordUserInfoData? = null
) : User, HasGuideUserData {

    // Helper properties
    val isWebUser: Boolean get() = webUser != null
    val isDiscordUser: Boolean get() = discordUserInfo != null
    val hasIdentitySource: Boolean get() = isWebUser || isDiscordUser

    // HasGuideUserData implementation
    override fun guideUserData(): GuideUserData = core

    // User interface implementation - delegate to available identity source
    override val id: String
        get() = webUser?.id ?: discordUserInfo?.id ?: core.id

    override val displayName: String
        get() = webUser?.displayName ?: discordUserInfo?.displayName ?: "Unknown"

    override val username: String
        get() = webUser?.userName ?: discordUserInfo?.username ?: "unknown"

    override val email: String?
        get() = webUser?.userEmail

    override fun toString(): String {
        return "GuideUser(displayName='$displayName')"
    }


}
