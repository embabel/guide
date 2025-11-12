package com.embabel.guide.domain.drivine;

import org.jetbrains.annotations.NotNull;

/**
 * Marker interface for types that contain Discord user information.
 * Provides access to the Discord user data component.
 */
public interface HasDiscordUserInfoData {

    /**
     * Get the Discord user info data
     * @return the Discord user info data
     */
    @NotNull
    DiscordUserInfoData getDiscordUserInfo();
}
