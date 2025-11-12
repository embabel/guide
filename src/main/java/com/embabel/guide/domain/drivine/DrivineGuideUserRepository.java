package com.embabel.guide.domain.drivine;

import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.drivine.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Drivine-based implementation of GuideUser repository.
 * Uses composition-based queries rather than OGM relationships.
 */
@Repository
public class DrivineGuideUserRepository {

    private final PersistenceManager manager;

    @Autowired
    public DrivineGuideUserRepository(@Qualifier("neo") PersistenceManager manager) {
        this.manager = manager;
    }

    /**
     * Find a GuideUser by Discord user ID, returning composed result
     */
    @Transactional(readOnly = true)
    public Optional<GuideUserWithDiscordUserInfo> findByDiscordUserId(String discordUserId) {
        String cypher = """
            MATCH (u:GuideUser)-[:IS_DISCORD_USER]->(d:DiscordUserInfo)
            WHERE d.id = $discordUserId
            RETURN {
              guideUserData: properties(u),
              discordUserInfo: properties(d)
            }
            """;

        return manager.optionalGetOne(
            QuerySpecification
                .withStatement(cypher)
                .bind(java.util.Map.of("discordUserId", discordUserId))
                .transform(GuideUserWithDiscordUserInfo.class)
        );
    }

    /**
     * Find a GuideUser by web user ID, returning composed result
     */
    @Transactional(readOnly = true)
    public Optional<GuideUserWithWebUser> findByWebUserId(String webUserId) {
        String cypher = """
            MATCH (u:GuideUser)-[:IS_WEB_USER]->(w:WebUser)
            WHERE w.id = $webUserId
            RETURN {
              guideUserData: properties(u),
              webUser: properties(w)
            }
            """;

        return manager.optionalGetOne(
            QuerySpecification
                .withStatement(cypher)
                .bind(java.util.Map.of("webUserId", webUserId))
                .transform(GuideUserWithWebUser.class)
        );
    }

    /**
     * Find the anonymous web user
     */
    @Transactional(readOnly = true)
    public Optional<GuideUserWithWebUser> findAnonymousWebUser() {
        String cypher = """
            MATCH (u:GuideUser)-[:IS_WEB_USER]->(w:WebUser:Anonymous)
            RETURN {
              guideUserData: properties(u),
              webUser: properties(w)
            }
            LIMIT 1
            """;

        Optional<GuideUserWithWebUser> guideUserWithWebUser = manager.optionalGetOne(
            QuerySpecification
                .withStatement(cypher)
                .bind(Map.of())
                .transform(GuideUserWithWebUser.class)
        );
        return guideUserWithWebUser;
    }

    /**
     * Find a GuideUser by username
     */
    @Transactional(readOnly = true)
    public Optional<GuideUserWithWebUser> findByWebUserName(String userName) {
        String cypher = """
            MATCH (u:GuideUser)-[:IS_WEB_USER]->(w:WebUser)
            WHERE w.userName = $userName
            RETURN {
              guideUserData: properties(u),
              webUser: properties(w)
            }
            """;

        return manager.optionalGetOne(
            QuerySpecification
                .withStatement(cypher)
                .bind(java.util.Map.of("userName", userName))
                .transform(GuideUserWithWebUser.class)
        );
    }

    /**
     * Create a new GuideUser with Discord info
     */
    @Transactional
    public GuideUserWithDiscordUserInfo createWithDiscord(GuideUserData guideUserData,
                                                          DiscordUserInfoData discordUserInfo) {
        String cypher = """
            CREATE (u:GuideUser)
            SET u += $guideUserProps
            CREATE (d:DiscordUserInfo)
            SET d += $discordProps
            CREATE (u)-[:IS_DISCORD_USER]->(d)
            RETURN {
              guideUserData: properties(u),
              discordUserInfo: properties(d)
            }
            """;

        return manager.getOne(
            QuerySpecification
                .withStatement(cypher)
                .bind(java.util.Map.of(
                    "guideUserProps", ObjectUtils.primitiveProps(guideUserData),
                    "discordProps", ObjectUtils.primitiveProps(discordUserInfo)
                ))
                .transform(GuideUserWithDiscordUserInfo.class)
        );
    }

    /**
     * Create a new GuideUser with WebUser info
     */
    @Transactional
    public GuideUserWithWebUser createWithWebUser(GuideUserData guideUserData,
                                                   WebUserData webUserData) {
        // Determine labels based on the WebUserData subtype
        String webUserLabels = webUserData instanceof AnonymousWebUserData
            ? "WebUser:Anonymous"
            : "WebUser";

        String cypher = """
            CREATE (u:GuideUser)
            SET u += $guideUserProps
            CREATE (w:%s)
            SET w += $webUserProps
            CREATE (u)-[:IS_WEB_USER]->(w)
            RETURN {
              guideUserData: properties(u),
              webUser: properties(w)
            }
            """.formatted(webUserLabels);

        return manager.getOne(
            QuerySpecification
                .withStatement(cypher)
                .bind(java.util.Map.of(
                    "guideUserProps", ObjectUtils.primitiveProps(guideUserData),
                    "webUserProps", ObjectUtils.primitiveProps(webUserData)
                ))
                .transform(GuideUserWithWebUser.class)
        );
    }

    /**
     * Update GuideUser persona
     */
    @Transactional
    public void updatePersona(String guideUserId, String persona) {
        String cypher = """
            MATCH (u:GuideUser {id: $id})
            SET u.persona = $persona
            """;

        manager.execute(
            QuerySpecification
                .withStatement(cypher)
                .bind(java.util.Map.of(
                    "id", guideUserId,
                    "persona", persona
                ))
                .transform(Void.class)
        );
    }

    /**
     * Update GuideUser custom prompt
     */
    @Transactional
    public void updateCustomPrompt(String guideUserId, String customPrompt) {
        String cypher = """
            MATCH (u:GuideUser {id: $id})
            SET u.customPrompt = $customPrompt
            """;

        manager.execute(
            QuerySpecification
                .withStatement(cypher)
                .bind(java.util.Map.of(
                    "id", guideUserId,
                    "customPrompt", customPrompt
                ))
        );
    }

    /**
     * Find a GuideUser by ID, returning as GuideUserWithWebUser or GuideUserWithDiscord
     * depending on what relationships exist
     */
    @Transactional(readOnly = true)
    public Optional<HasGuideUserData> findById(String id) {
        // First try to find with Discord info
        String cypher = """
            MATCH (u:GuideUser {id: $id})
            OPTIONAL MATCH (u)-[:IS_DISCORD_USER]->(d:DiscordUserInfo)
            OPTIONAL MATCH (u)-[:IS_WEB_USER]->(w:WebUser)
            RETURN {
              guideUserData: properties(u),
              discordUserInfo: properties(d),
              webUser: properties(w),
              hasDiscord: d IS NOT NULL,
              hasWebUser: w IS NOT NULL
            }
            """;

        var result = manager.optionalGetOne(
            QuerySpecification
                .withStatement(cypher)
                .bind(java.util.Map.of("id", id))
                .transform(java.util.Map.class)
        );

        return result.map(map -> {
            boolean hasDiscord = (Boolean) map.getOrDefault("hasDiscord", false);
            boolean hasWebUser = (Boolean) map.getOrDefault("hasWebUser", false);

            GuideUserData userData = new GuideUserData();
            Map<String, Object> userDataMap = (Map<String, Object>) map.get("guideUserData");
            if (userDataMap != null) {
                userData.setId((String) userDataMap.get("id"));
                userData.setPersona((String) userDataMap.get("persona"));
                userData.setCustomPrompt((String) userDataMap.get("customPrompt"));
            }

            if (hasDiscord) {
                Map<String, Object> discordMap = (Map<String, Object>) map.get("discordUserInfo");
                DiscordUserInfoData discordData = null;
                if (discordMap != null) {
                    discordData = new DiscordUserInfoData(
                        (String) discordMap.get("id"),
                        (String) discordMap.get("username"),
                        (String) discordMap.get("discriminator"),
                        (String) discordMap.get("displayName"),
                        (Boolean) discordMap.get("isBot"),
                        (String) discordMap.get("avatarUrl")
                    );
                } else {
                    discordData = new DiscordUserInfoData();
                }
                return new GuideUserWithDiscordUserInfo(userData, discordData);
            } else if (hasWebUser) {
                java.util.Map<String, Object> webUserMap = (java.util.Map<String, Object>) map.get("webUser");
                WebUserData webUserData = new WebUserData();
                if (webUserMap != null) {
                    webUserData.setId((String) webUserMap.get("id"));
                    webUserData.setDisplayName((String) webUserMap.get("displayName"));
                    webUserData.setUserName((String) webUserMap.get("userName"));
                    webUserData.setUserEmail((String) webUserMap.get("userEmail"));
                    webUserData.setPasswordHash((String) webUserMap.get("passwordHash"));
                    webUserData.setRefreshToken((String) webUserMap.get("refreshToken"));
                }
                return new GuideUserWithWebUser(userData, webUserData);
            }

            // Return a plain GuideUserData when there are no relationships
            return userData;
        });
    }

    /**
     * Save a GuideUser - this is a simplified version that updates persona/customPrompt
     */
    @Transactional
    public HasGuideUserData save(com.embabel.guide.domain.GuideUser guideUser) {
        String cypher = """
            MERGE (u:GuideUser {id: $id})
            SET u.persona = $persona,
                u.customPrompt = $customPrompt
            """;

        manager.execute(
            QuerySpecification
                .withStatement(cypher)
                .bind(java.util.Map.of(
                    "id", guideUser.getId(),
                    "persona", guideUser.persona() != null ? guideUser.persona() : "",
                    "customPrompt", guideUser.customPersona() != null ? guideUser.customPersona() : ""
                ))
        );

        // Return the updated user
        return findById(guideUser.getId()).orElseThrow();
    }

    /**
     * Delete all GuideUsers (for testing) - also deletes related WebUser and DiscordUserInfo nodes
     */
    @Transactional
    public void deleteAllGuideUsers() {
        String cypher = """
            MATCH (u:GuideUser)
            OPTIONAL MATCH (u)-[:IS_WEB_USER]->(w:WebUser)
            OPTIONAL MATCH (u)-[:IS_DISCORD_USER]->(d:DiscordUserInfo)
            DETACH DELETE u, w, d
            """;

        manager.execute(
            QuerySpecification
                .withStatement(cypher)
                .bind(Map.of())
        );
    }

    /**
     * Find all GuideUsers (for testing)
     */
    @Transactional(readOnly = true)
    public java.util.List<HasGuideUserData> findAllGuideUsers() {
        String cypher = """
            MATCH (u:GuideUser)
            OPTIONAL MATCH (u)-[:IS_DISCORD_USER]->(d:DiscordUserInfo)
            OPTIONAL MATCH (u)-[:IS_WEB_USER]->(w:WebUser)
            RETURN {
              guideUserData: properties(u),
              discordUserInfo: properties(d),
              webUser: properties(w),
              hasDiscord: d IS NOT NULL,
              hasWebUser: w IS NOT NULL
            }
            """;

        return manager.query(
            QuerySpecification
                .withStatement(cypher)
                .bind(Map.of())
                .transform(java.util.Map.class)
        ).stream()
            .map(map -> {
                boolean hasDiscord = (Boolean) map.getOrDefault("hasDiscord", false);
                boolean hasWebUser = (Boolean) map.getOrDefault("hasWebUser", false);

                GuideUserData userData = new GuideUserData();
                java.util.Map<String, Object> userDataMap = (java.util.Map<String, Object>) map.get("guideUserData");
                if (userDataMap != null) {
                    userData.setId((String) userDataMap.get("id"));
                    userData.setPersona((String) userDataMap.get("persona"));
                    userData.setCustomPrompt((String) userDataMap.get("customPrompt"));
                }

                if (hasDiscord) {
                    java.util.Map<String, Object> discordMap = (java.util.Map<String, Object>) map.get("discordUserInfo");
                    DiscordUserInfoData discordData = null;
                    if (discordMap != null) {
                        discordData = new DiscordUserInfoData(
                            (String) discordMap.get("id"),
                            (String) discordMap.get("username"),
                            (String) discordMap.get("discriminator"),
                            (String) discordMap.get("displayName"),
                            (Boolean) discordMap.get("isBot"),
                            (String) discordMap.get("avatarUrl")
                        );
                    } else {
                        discordData = new DiscordUserInfoData();
                    }
                    return (HasGuideUserData) new GuideUserWithDiscordUserInfo(userData, discordData);
                } else if (hasWebUser) {
                    java.util.Map<String, Object> webUserMap = (java.util.Map<String, Object>) map.get("webUser");
                    WebUserData webUserData = new WebUserData();
                    if (webUserMap != null) {
                        webUserData.setId((String) webUserMap.get("id"));
                        webUserData.setDisplayName((String) webUserMap.get("displayName"));
                        webUserData.setUserName((String) webUserMap.get("userName"));
                        webUserData.setUserEmail((String) webUserMap.get("userEmail"));
                        webUserData.setPasswordHash((String) webUserMap.get("passwordHash"));
                        webUserData.setRefreshToken((String) webUserMap.get("refreshToken"));
                    }
                    return (HasGuideUserData) new GuideUserWithWebUser(userData, webUserData);
                }

                // Return just the GuideUserData if no relationships
                return (HasGuideUserData) userData;
            })
            .collect(java.util.stream.Collectors.toList());
    }

}
