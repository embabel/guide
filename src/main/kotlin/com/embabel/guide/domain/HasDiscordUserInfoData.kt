package com.embabel.guide.domain

/**
 * Marker interface for types that contain Discord user information.
 * Provides access to the Discord user data component.
 */
interface HasDiscordUserInfoData {

    /**
     * Get the Discord user info data
     * @return the Discord user info data
     */
    val discordUserInfo: DiscordUserInfoData
}
