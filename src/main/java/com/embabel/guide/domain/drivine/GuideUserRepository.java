package com.embabel.guide.domain.drivine;

import org.drivine.manager.GraphObjectManager;
import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GraphObjectManager-based repository for GuideUser operations.
 * Uses @GraphView annotated GuideUserView for automatic graph traversal.
 *
 * GraphObjectManager leverages @GraphView and @GraphRelationship annotations
 * to automatically load the complete graph structure. We use string-based WHERE
 * clauses for filtering, which work well for simple root properties and direct
 * relationship targets.
 *
 * Note: For complex nested GraphView filtering, Kotlin @GraphView with DSL
 * would be preferred, but our filtering needs are simple enough for string WHERE clauses.
 */
@Repository
public class GuideUserRepository {

    private final GraphObjectManager graphManager;
    private final PersistenceManager persistenceManager;

    @Autowired
    public GuideUserRepository(GraphObjectManager graphManager,
                               @Qualifier("neo") PersistenceManager persistenceManager) {
        this.graphManager = graphManager;
        this.persistenceManager = persistenceManager;
    }

    /**
     * Find a GuideUser by ID - uses GraphObjectManager.load()
     * Automatically loads all relationships defined in @GraphView
     */
    @Transactional(readOnly = true)
    public Optional<GuideUserView> findById(@NotNull String id) {
        GuideUserView result = graphManager.load(id, GuideUserView.class);
        return Optional.ofNullable(result);
    }

    /**
     * Find all GuideUsers - uses GraphObjectManager.loadAll()
     * Automatically loads all relationships for each GuideUser
     */
    @Transactional(readOnly = true)
    public List<GuideUserView> findAllGuideUsers() {
        return graphManager.loadAll(GuideUserView.class);
    }

    /**
     * Find a GuideUser by Discord user ID
     * Uses string-based WHERE clause to filter on relationship target properties
     */
    @Transactional(readOnly = true)
    public Optional<GuideUserView> findByDiscordUserId(@NotNull String discordUserId) {
        // The relationship field is 'discordUserInfo', so the target variable is 'discordUserInfo'
        List<GuideUserView> results = graphManager.loadAll(
            GuideUserView.class,
            "discordUserInfo.id = '" + discordUserId + "'"
        );

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Find a GuideUser by web user ID
     */
    @Transactional(readOnly = true)
    public Optional<GuideUserView> findByWebUserId(@NotNull String webUserId) {
        // The relationship field is 'webUser', so the target variable is 'webUser'
        List<GuideUserView> results = graphManager.loadAll(
            GuideUserView.class,
            "webUser.id = '" + webUserId + "'"
        );

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Find the anonymous web user
     * Note: Since we need to check for the Anonymous label, we use EXISTS for relationship filtering
     */
    @Transactional(readOnly = true)
    public Optional<GuideUserView> findAnonymousWebUser() {
        // Use EXISTS to check for the Anonymous label on the WebUser node
        List<GuideUserView> results = graphManager.loadAll(
            GuideUserView.class,
            "EXISTS { (guideUser)-[:IS_WEB_USER]->(w:WebUser:Anonymous) }"
        );

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Find a GuideUser by web username
     */
    @Transactional(readOnly = true)
    public Optional<GuideUserView> findByWebUserName(@NotNull String userName) {
        List<GuideUserView> results = graphManager.loadAll(
            GuideUserView.class,
            "webUser.userName = '" + userName + "'"
        );

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Create a new GuideUser with Discord info
     * Uses PersistenceManager for creation, then loads via GraphObjectManager
     */
    @Transactional
    public GuideUserView createWithDiscord(@NotNull GuideUserData guideUserData,
                                           @NotNull DiscordUserInfoData discordUserInfo) {
        String cypher = """
            CREATE (u:GuideUser)
            SET u += $guideUserProps
            CREATE (d:DiscordUserInfo)
            SET d += $discordProps
            CREATE (u)-[:IS_DISCORD_USER]->(d)
            RETURN u.id as id
            """;

        Map<String, Object> result = persistenceManager.getOne(
            QuerySpecification
                .withStatement(cypher)
                .bindObject("guideUserProps", guideUserData)
                .bindObject("discordProps", discordUserInfo)
                .transform(Map.class)
        );

        String guideUserId = (String) result.get("id");
        return findById(guideUserId).orElseThrow();
    }

    /**
     * Create a new GuideUser with WebUser info
     */
    @Transactional
    public GuideUserView createWithWebUser(@NotNull GuideUserData guideUserData,
                                           @NotNull WebUserData webUserData) {
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
            RETURN u.id as id
            """.formatted(webUserLabels);

        Map<String, Object> result = persistenceManager.getOne(
            QuerySpecification
                .withStatement(cypher)
                .bindObject("guideUserProps", guideUserData)
                .bindObject("webUserProps", webUserData)
                .transform(Map.class)
        );

        String guideUserId = (String) result.get("id");
        return findById(guideUserId).orElseThrow();
    }

    /**
     * Update GuideUser persona
     */
    @Transactional
    public void updatePersona(@NotNull String guideUserId, @NotNull String persona) {
        String cypher = """
            MATCH (u:GuideUser {id: $id})
            SET u.persona = $persona
            """;

        persistenceManager.execute(
            QuerySpecification
                .withStatement(cypher)
                .bind(Map.of(
                    "id", guideUserId,
                    "persona", persona
                ))
        );
    }

    /**
     * Update GuideUser custom prompt
     */
    @Transactional
    public void updateCustomPrompt(@NotNull String guideUserId, @NotNull String customPrompt) {
        String cypher = """
            MATCH (u:GuideUser {id: $id})
            SET u.customPrompt = $customPrompt
            """;

        persistenceManager.execute(
            QuerySpecification
                .withStatement(cypher)
                .bind(Map.of(
                    "id", guideUserId,
                    "customPrompt", customPrompt
                ))
        );
    }

    /**
     * Save/update a GuideUser view
     */
    @Transactional
    public GuideUserView save(@NotNull GuideUserView guideUserView) {
        GuideUserData guideUser = guideUserView.getGuideUser();

        String cypher = """
            MERGE (u:GuideUser {id: $id})
            SET u += $props
            """;

        persistenceManager.execute(
            QuerySpecification
                .withStatement(cypher)
                .bindObject("props", guideUser)
                .bind(Map.of("id", guideUser.getId()))
        );

        // Return the updated view - GraphObjectManager will load relationships
        return findById(guideUser.getId()).orElseThrow();
    }

    /**
     * Delete all GuideUsers (for testing)
     * Also deletes related WebUser and DiscordUserInfo nodes
     */
    @Transactional
    public void deleteAllGuideUsers() {
        String cypher = """
            MATCH (u:GuideUser)
            OPTIONAL MATCH (u)-[:IS_WEB_USER]->(w:WebUser)
            OPTIONAL MATCH (u)-[:IS_DISCORD_USER]->(d:DiscordUserInfo)
            DETACH DELETE u, w, d
            """;

        persistenceManager.execute(
            QuerySpecification
                .withStatement(cypher)
                .bind(Map.of())
        );
    }
}