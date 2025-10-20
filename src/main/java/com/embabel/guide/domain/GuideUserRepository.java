package com.embabel.guide.domain;

import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.Optional;

public interface GuideUserRepository extends Neo4jRepository<GuideUser, String> {

    @Query("MATCH (u:GuideUser)-[r:IS_DISCORD_USER]->(d:DiscordUserInfo) WHERE d.id = $discordUserId RETURN u, r, d")
    Optional<GuideUser> findByDiscordUserId(String discordUserId);

    @Query("MATCH (u:GuideUser)-[r:IS_WEB_USER]->(w:WebUser) WHERE w.userId = $webUserId RETURN u, r, w")
    Optional<GuideUser> findByWebUserId(String webUserId);

    @Query("MATCH (u:GuideUser)-[r:IS_WEB_USER]->(w:WebUser:Anonymous) RETURN u, r, w LIMIT 1")
    Optional<GuideUser> findAnonymousWebUser();

}
