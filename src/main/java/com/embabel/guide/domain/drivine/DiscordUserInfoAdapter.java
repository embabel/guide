package com.embabel.guide.domain.drivine;

import com.embabel.agent.discord.DiscordUserInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adapter to convert DiscordUserInfoData to DiscordUserInfo interface.
 * Used when converting composed Drivine results back to domain entities.
 */
public class DiscordUserInfoAdapter implements DiscordUserInfo {

    private final DiscordUserInfoData data;

    public DiscordUserInfoAdapter(DiscordUserInfoData data) {
        this.data = data;
    }

    @NotNull
    @Override
    public String getId() {
        return data.getId();
    }

    @NotNull
    @Override
    public String getUsername() {
        return data.getUsername();
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return data.getDisplayName();
    }

    @NotNull
    @Override
    public String getDiscriminator() {
        return data.getDiscriminator();
    }

    @Nullable
    @Override
    public String getAvatarUrl() {
        return data.getAvatarUrl();
    }

    @Override
    public boolean isBot() {
        return data.getIsBot() != null && data.getIsBot();
    }
}