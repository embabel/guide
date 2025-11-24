package com.embabel.guide.domain.drivine;

import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.drivine.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * Drivine-based implementation of GuideUser repository.
 * Uses composition-based queries rather than OGM relationships.
 */
@Repository
public class DrivineGuideUserRepository {

    private final PersistenceManager manager;
    private final GuideUserMapper mapper;

    @Autowired
    public DrivineGuideUserRepository(@Qualifier("neo") PersistenceManager manager,
                                      GuideUserMapper mapper) {
        this.manager = manager;
        this.mapper = mapper;
    }

    /**
     * Find a GuideUser by Discord user ID, returning a composed result
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
                .bind(Map.of("discordUserId", discordUserId))
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
                .bind(Map.of("webUserId", webUserId))
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

        return manager.optionalGetOne(
            QuerySpecification
                .withStatement(cypher)
                .bind(Map.of())
                .transform(GuideUserWithWebUser.class)
        );
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
                .bind(Map.of("userName", userName))
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
                .bind(Map.of(
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
                .bind(Map.of(
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
                .bind(Map.of(
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
                .bind(Map.of(
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
              webUser: properties(w)
            }
            """;

        var result = manager.optionalGetOne(
            QuerySpecification
                .withStatement(cypher)
                .bind(Map.of("id", id))
                .transform(Map.class)
        );

        return result.map(mapper::mapToGuideUserComposite);
    }

    /**
     * Save a GuideUser - this is a simplified version that updates persona/customPrompt
     */
    @Transactional
    public HasGuideUserData save(GuideUserData guideUser) {
        String cypher = """
            MERGE (u:GuideUser {id: $id})
            SET u += props
            """;

        Map<String, Object> props = ObjectUtils.primitiveProps(guideUser);

        manager.execute(
            QuerySpecification
                .withStatement(cypher)
                .bind(Map.of(
                    "propsid", props
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
    public List<HasGuideUserData> findAllGuideUsers() {
        String cypher = """
            MATCH (u:GuideUser)
            OPTIONAL MATCH (u)-[:IS_DISCORD_USER]->(d:DiscordUserInfo)
            OPTIONAL MATCH (u)-[:IS_WEB_USER]->(w:WebUser)
            RETURN {
              guideUserData: properties(u),
              discordUserInfo: properties(d),
              webUser: properties(w)
            }
            """;

        return manager.query(
            QuerySpecification
                .withStatement(cypher)
                .bind(Map.of())
                .transform(Map.class)
        ).stream()
            .map(mapper::mapToGuideUserComposite)
            .collect(Collectors.toList());
    }


}
