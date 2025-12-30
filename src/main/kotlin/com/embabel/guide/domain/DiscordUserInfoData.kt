package com.embabel.guide.domain

import com.embabel.agent.discord.DiscordUserInfo
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Simple data representation of DiscordUserInfo properties for Drivine composition.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DiscordUserInfoData(
    var id: String? = null,
    var username: String? = null,
    var discriminator: String? = null,
    var displayName: String? = null,
    var isBot: Boolean? = null,
    var avatarUrl: String? = null
) {
    /**
     * Convert to DiscordUserInfo interface implementation.
     */
    fun toDiscordUserInfo(): DiscordUserInfo = object : DiscordUserInfo {
        override val id: String get() = this@DiscordUserInfoData.id!!
        override val username: String get() = this@DiscordUserInfoData.username!!
        override val displayName: String get() = this@DiscordUserInfoData.displayName!!
        override val discriminator: String get() = this@DiscordUserInfoData.discriminator!!
        override val avatarUrl: String? get() = this@DiscordUserInfoData.avatarUrl
        override val isBot: Boolean get() = this@DiscordUserInfoData.isBot == true
    }

    override fun toString(): String =
        "DiscordUserInfoData{id='$id', username='$username', displayName='$displayName'}"
}
