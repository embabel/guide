package com.embabel.guide.domain.drivine;

import com.embabel.agent.api.identity.User;
import com.embabel.agent.discord.DiscordUser;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Composed result type for GuideUser queries that include Discord user information.
 * Following Drivine's composition philosophy rather than ORM relationships.
 * Implements User interface by delegating to the Discord user data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GuideUserWithDiscordUserInfo implements HasGuideUserData, HasDiscordUserInfoData, User {

    @NotNull
    private GuideUserData guideUserData;

    @NotNull
    private DiscordUserInfoData discordUserInfo;

    // No-arg constructor for Jackson
    public GuideUserWithDiscordUserInfo() {
    }

    public GuideUserWithDiscordUserInfo(GuideUserData guideUserData, DiscordUserInfoData discordUserInfo) {
        this.guideUserData = guideUserData;
        this.discordUserInfo = discordUserInfo;
    }

    /**
     * Factory method to create a new GuideUserWithDiscord from a DiscordUser
     */
    public static GuideUserWithDiscordUserInfo fromDiscordUser(DiscordUser discordUser) {
        GuideUserData guideUserData = new GuideUserData(
                UUID.randomUUID().toString(),
                null,
                null
        );

        var du = discordUser.getDiscordUser();
        DiscordUserInfoData discordData = new DiscordUserInfoData(
                du.getId(),
                du.getUsername(),
                du.getDiscriminator(),
                du.getDisplayName(),
                du.isBot(),
                du.getAvatarUrl()
        );

        return new GuideUserWithDiscordUserInfo(guideUserData, discordData);
    }

    @Override
    @NotNull
    public GuideUserData guideUserData() {
        return guideUserData;
    }

    public GuideUserData getGuideUserData() {
        return guideUserData;
    }

    public void setGuideUserData(GuideUserData guideUserData) {
        this.guideUserData = guideUserData;
    }

    @NotNull
    @Override
    public DiscordUserInfoData getDiscordUserInfo() {
        return discordUserInfo;
    }

    public void setDiscordUserInfo(DiscordUserInfoData discordUserInfo) {
        this.discordUserInfo = discordUserInfo;
    }

    @Override
    public String toString() {
        return "GuideUserWithDiscord{" +
                "guideUserData=" + guideUserData +
                ", discordUserInfo=" + discordUserInfo +
                '}';
    }

    // User interface delegation to DiscordUserInfo

    @NotNull
    @Override
    public String getId() {
        return discordUserInfo.getId();
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return discordUserInfo.getDisplayName();
    }

    @NotNull
    @Override
    public String getUsername() {
        return discordUserInfo.getUsername();
    }

    @Nullable
    @Override
    public String getEmail() {
        // Discord users don't typically have email in this context
        return null;
    }
}
