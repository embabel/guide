package com.embabel.guide.domain;

import com.embabel.agent.discord.DiscordUser;
import com.embabel.agent.identity.User;
import org.jetbrains.annotations.NotNull;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.UUID;

/**
 * Persona if set is a default personality for the user
 * Custom prompt if set is a custom prompt for the user
 */
@NodeEntity
public class GuideUser implements User {

    @Id
    private String id;

    @Nullable
    private String persona = null;

    @Nullable
    private String customPrompt = null;

    @Nullable
    @Relationship(type = "IS_DISCORD_USER", direction = Relationship.Direction.OUTGOING)
    private MappedDiscordUserInfo discordUserInfo = null;

    @Nullable
    @Relationship(type = "IS_WEB_USER", direction = Relationship.Direction.OUTGOING)
    private WebUser webUser = null;


    public static GuideUser createFromDiscord(DiscordUser user) {
        var guideUser = new GuideUser();
        guideUser.id = UUID.randomUUID().toString();
        guideUser.discordUserInfo = new MappedDiscordUserInfo(user.getDiscordUser());
        return guideUser;
    }

    public static GuideUser createFromWebUser(WebUser webUser) {
        var guideUser = new GuideUser();
        guideUser.id = UUID.randomUUID().toString();
        guideUser.webUser = webUser;
        return guideUser;
    }

    // TODO personality

    public GuideUser() {
        this.id = UUID.randomUUID().toString();
    }

    public void setPersona(@NonNull String persona) {
        this.persona = persona;
    }

    public @Nullable String persona() {
        return persona;
    }

    public void setCustomPrompt(@NonNull String customPrompt) {
        this.customPrompt = customPrompt;
    }

    public @Nullable String customPersona() {
        return customPrompt;
    }

    @NotNull
    public String getId() {
        return id;
    }

    public @Nullable MappedDiscordUserInfo discordUserInfo() {
        return discordUserInfo;
    }

    public @Nullable WebUser getWebUser() {
        return webUser;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        if (webUser != null) {
            return webUser.getDisplayName();
        }
        if (discordUserInfo != null) {
            return discordUserInfo.getDisplayName();
        }
        return id;
    }

    @NotNull
    @Override
    public String getUsername() {
        if (webUser != null) {
            return webUser.getUsername();
        }
        if (discordUserInfo != null) {
            return discordUserInfo.getUsername();
        }
        return id;
    }

    @Nullable
    @Override
    public String getEmail() {
        if (webUser != null) {
            return webUser.getEmail();
        }
        return null;
    }

    @Override
    public String toString() {
        return "GuideUser{" +
                "id='" + id + '\'' +
                '}';
    }
}
