package com.embabel.guide.domain

import com.embabel.agent.api.identity.User
import com.embabel.agent.discord.DiscordUser
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.UUID

/**
 * Composed result type for GuideUser queries that include Discord user information.
 * Following Drivine's composition philosophy rather than ORM relationships.
 * Implements User interface by delegating to the Discord user data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GuideUserWithDiscordUserInfo(
    var guideUserData: GuideUserData,
    override var discordUserInfo: DiscordUserInfoData
) : HasGuideUserData, HasDiscordUserInfoData, User {

    override fun guideUserData(): GuideUserData = guideUserData

    // User interface delegation to DiscordUserInfo
    override val id: String
        get() = discordUserInfo.id!!

    override val displayName: String
        get() = discordUserInfo.displayName!!

    override val username: String
        get() = discordUserInfo.username!!

    override val email: String?
        get() = null // Discord users don't typically have email in this context

    companion object {
        /**
         * Factory method to create a new GuideUserWithDiscord from a DiscordUser
         */
        @JvmStatic
        fun fromDiscordUser(discordUser: DiscordUser): GuideUserWithDiscordUserInfo {
            val guideUserData = GuideUserData(
                id = UUID.randomUUID().toString(),
                persona = null,
                customPrompt = null
            )

            val du = discordUser.discordUser
            val discordData = DiscordUserInfoData(
                id = du.id,
                username = du.username,
                discriminator = du.discriminator,
                displayName = du.displayName,
                isBot = du.isBot,
                avatarUrl = du.avatarUrl
            )

            return GuideUserWithDiscordUserInfo(guideUserData, discordData)
        }
    }
}
