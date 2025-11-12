package com.embabel.guide.domain.drivine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Simple data representation of DiscordUserInfo properties for Drivine composition.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DiscordUserInfoData {

    private String id;
    private String username;
    private String discriminator;
    private String displayName;
    private Boolean isBot;

    @Nullable
    private String avatarUrl;

    // No-arg constructor for Jackson
    public DiscordUserInfoData() {
    }

    public DiscordUserInfoData(String id, String username, String discriminator,
                               String displayName, Boolean isBot, @Nullable String avatarUrl) {
        this.id = id;
        this.username = username;
        this.discriminator = discriminator;
        this.displayName = displayName;
        this.isBot = isBot;
        this.avatarUrl = avatarUrl;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDiscriminator() {
        return discriminator;
    }

    public void setDiscriminator(String discriminator) {
        this.discriminator = discriminator;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Boolean getIsBot() {
        return isBot;
    }

    public void setIsBot(Boolean isBot) {
        this.isBot = isBot;
    }

    @Nullable
    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(@Nullable String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    @Override
    public String toString() {
        return "DiscordUserInfoData{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", displayName='" + displayName + '\'' +
                '}';
    }
}