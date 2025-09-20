package com.embabel.guide.domain;


import com.embabel.agent.discord.DiscordUserInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;

@NodeEntity("DiscordUserInfo")
public class MappedDiscordUserInfo implements DiscordUserInfo {

    @Id
    private final String id;
    private final String username;
    private final String discriminator;
    private final String displayName;
    private final Boolean isBot;
    private final String avatarUrl;

    private MappedDiscordUserInfo() {
        this.id = "";
        this.username = "";
        this.discriminator = "";
        this.displayName = "";
        this.isBot = false;
        this.avatarUrl = null;
    }
    
    public MappedDiscordUserInfo(
            DiscordUserInfo discordUser) {
        this.id = discordUser.getId();
        this.username = discordUser.getUsername();
        this.discriminator = discordUser.getDiscriminator();
        this.displayName = discordUser.getDisplayName();
        this.isBot = discordUser.isBot();
        this.avatarUrl = discordUser.getAvatarUrl();
    }

    @NotNull
    @Override
    public String getId() {
        return id;
    }

    @NotNull
    @Override
    public String getUsername() {
        return username;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return displayName;
    }

    @NotNull
    @Override
    public String getDiscriminator() {
        return discriminator;
    }

    @Nullable
    @Override
    public String getAvatarUrl() {
        return avatarUrl;
    }

    @Override
    public boolean isBot() {
        return isBot;
    }
}
