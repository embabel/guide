package com.embabel.guide.domain.drivine;

import org.drivine.annotation.Direction;
import org.drivine.annotation.GraphView;
import org.drivine.annotation.GraphRelationship;
import org.drivine.annotation.Root;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * GraphView for GuideUser with optional relationships to WebUser or DiscordUserInfo.
 *
 * This represents the complete graph structure:
 * - (GuideUser) node properties via GuideUserData
 * - Optional [:IS_WEB_USER] relationship to (WebUser)
 * - Optional [:IS_DISCORD_USER] relationship to (DiscordUserInfo)
 *
 * A GuideUser can have either a WebUser OR a DiscordUserInfo (not both).
 * Use helper methods to safely access the appropriate user type.
 */
@GraphView
public class GuideUserView implements HasGuideUserData {

    /**
     * Core GuideUser node properties (root of the graph view)
     */
    @Root
    @NotNull
    public GuideUserData guideUser;

    /**
     * Optional relationship to WebUser node
     */
    @GraphRelationship(type = "IS_WEB_USER", direction = Direction.OUTGOING)
    @Nullable
    public WebUserData webUser;

    /**
     * Optional relationship to DiscordUserInfo node
     */
    @GraphRelationship(type = "IS_DISCORD_USER", direction = Direction.OUTGOING)
    @Nullable
    public DiscordUserInfoData discordUserInfo;

    // No-arg constructor for Jackson
    public GuideUserView() {
    }

    public GuideUserView(@NotNull GuideUserData guideUser,
                         @Nullable WebUserData webUser,
                         @Nullable DiscordUserInfoData discordUserInfo) {
        this.guideUser = guideUser;
        this.webUser = webUser;
        this.discordUserInfo = discordUserInfo;
    }

    @NotNull
    public GuideUserData getGuideUser() {
        return guideUser;
    }

    public void setGuideUser(@NotNull GuideUserData guideUser) {
        this.guideUser = guideUser;
    }

    @Nullable
    public WebUserData getWebUser() {
        return webUser;
    }

    public void setWebUser(@Nullable WebUserData webUser) {
        this.webUser = webUser;
    }

    @Nullable
    public DiscordUserInfoData getDiscordUserInfo() {
        return discordUserInfo;
    }

    public void setDiscordUserInfo(@Nullable DiscordUserInfoData discordUserInfo) {
        this.discordUserInfo = discordUserInfo;
    }

    @Override
    @NotNull
    public GuideUserData guideUserData() {
        return guideUser;
    }

    // Helper methods for type-safe access

    /**
     * Checks if this GuideUser is a web user
     */
    public boolean isWebUser() {
        return webUser != null;
    }

    /**
     * Checks if this GuideUser is a Discord user
     */
    public boolean isDiscordUser() {
        return discordUserInfo != null;
    }

    /**
     * Gets this GuideUser as a GuideUserWithWebUser if it is a web user.
     * Throws IllegalStateException if this is not a web user.
     */
    @NotNull
    public GuideUserWithWebUser asWebUser() {
        if (webUser == null) {
            throw new IllegalStateException("GuideUser is not a web user");
        }
        return new GuideUserWithWebUser(guideUser, webUser);
    }

    /**
     * Gets this GuideUser as a GuideUserWithDiscordUserInfo if it is a Discord user.
     * Throws IllegalStateException if this is not a Discord user.
     */
    @NotNull
    public GuideUserWithDiscordUserInfo asDiscordUser() {
        if (discordUserInfo == null) {
            throw new IllegalStateException("GuideUser is not a Discord user");
        }
        return new GuideUserWithDiscordUserInfo(guideUser, discordUserInfo);
    }

    /**
     * Gets the display name regardless of user type
     */
    @NotNull
    public String getDisplayName() {
        if (webUser != null) {
            return webUser.getDisplayName();
        } else if (discordUserInfo != null) {
            return discordUserInfo.getDisplayName();
        } else {
            return "Unknown User";
        }
    }

    @Override
    public String toString() {
        return "GuideUserView{" +
                "guideUser=" + guideUser +
                ", webUser=" + webUser +
                ", discordUserInfo=" + discordUserInfo +
                '}';
    }
}