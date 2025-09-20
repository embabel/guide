package com.embabel.guide.domain;

import com.embabel.agent.discord.DiscordUser;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.springframework.lang.Nullable;

import java.util.UUID;

@NodeEntity
public class GuideUser {

    @Id
    private String id;

    @Nullable
    private String discordUserId;

    // TODO persist DiscordUser, needs to be a node

    public static GuideUser createFromDiscord(DiscordUser user) {
        GuideUser guideUser = new GuideUser();
        guideUser.id = UUID.randomUUID().toString();
        guideUser.discordUserId = user.getId();
        return guideUser;
    }
    // TODO personality

    public GuideUser() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "GuideUser{" +
                "id='" + id + '\'' +
                '}';
    }
}
