package com.embabel.guide.domain

import org.drivine.manager.PersistenceManager
import org.drivine.query.QuerySpecification
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

/**
 * Drivine-based implementation of GuideUser repository.
 * Uses composition-based queries rather than OGM relationships.
 */
@Repository
class DrivineGuideUserRepository(
    @Qualifier("neo") private val manager: PersistenceManager,
    private val mapper: GuideUserMapper
) {

    /**
     * Find a GuideUser by Discord user ID, returning a composed result
     */
    @Transactional(readOnly = true)
    fun findByDiscordUserId(discordUserId: String): Optional<GuideUserWithDiscordUserInfo> {
        val cypher = """
            MATCH (u:GuideUser)-[:IS_DISCORD_USER]->(d:DiscordUserInfo)
            WHERE d.id = ${'$'}discordUserId
            RETURN {
              guideUserData: properties(u),
              discordUserInfo: properties(d)
            }
            """

        return manager.optionalGetOne(
            QuerySpecification
                .withStatement(cypher)
                .bind(mapOf("discordUserId" to discordUserId))
                .transform(GuideUserWithDiscordUserInfo::class.java)
        )
    }

    /**
     * Find a GuideUser by web user ID, returning composed result
     */
    @Transactional(readOnly = true)
    fun findByWebUserId(webUserId: String): Optional<GuideUserWithWebUser> {
        val cypher = """
            MATCH (u:GuideUser)-[:IS_WEB_USER]->(w:WebUser)
            WHERE w.id = ${'$'}webUserId
            RETURN {
              guideUserData: properties(u),
              webUser: properties(w)
            }
            """

        return manager.optionalGetOne(
            QuerySpecification
                .withStatement(cypher)
                .bind(mapOf("webUserId" to webUserId))
                .transform(GuideUserWithWebUser::class.java)
        )
    }

    /**
     * Find the anonymous web user
     */
    @Transactional(readOnly = true)
    fun findAnonymousWebUser(): Optional<GuideUserWithWebUser> {
        val cypher = """
            MATCH (u:GuideUser)-[:IS_WEB_USER]->(w:WebUser:Anonymous)
            RETURN {
              guideUserData: properties(u),
              webUser: properties(w)
            }
            LIMIT 1
            """

        return manager.optionalGetOne(
            QuerySpecification
                .withStatement(cypher)
                .bind(emptyMap<String, Any>())
                .transform(GuideUserWithWebUser::class.java)
        )
    }

    /**
     * Find a GuideUser by username
     */
    @Transactional(readOnly = true)
    fun findByWebUserName(userName: String): Optional<GuideUserWithWebUser> {
        val cypher = """
            MATCH (u:GuideUser)-[:IS_WEB_USER]->(w:WebUser)
            WHERE w.userName = ${'$'}userName
            RETURN {
              guideUserData: properties(u),
              webUser: properties(w)
            }
            """

        return manager.optionalGetOne(
            QuerySpecification
                .withStatement(cypher)
                .bind(mapOf("userName" to userName))
                .transform(GuideUserWithWebUser::class.java)
        )
    }

    /**
     * Create a new GuideUser with Discord info
     */
    @Transactional
    fun createWithDiscord(
        guideUserData: GuideUserData,
        discordUserInfo: DiscordUserInfoData
    ): GuideUserWithDiscordUserInfo {
        val cypher = """
            CREATE (u:GuideUser)
            SET u += ${'$'}guideUserProps
            CREATE (d:DiscordUserInfo)
            SET d += ${'$'}discordProps
            CREATE (u)-[:IS_DISCORD_USER]->(d)
            RETURN {
              guideUserData: properties(u),
              discordUserInfo: properties(d)
            }
            """

        return manager.getOne(
            QuerySpecification
                .withStatement(cypher)
                .bindObject("guideUserProps", guideUserData)
                .bindObject("discordProps", discordUserInfo)
                .transform(GuideUserWithDiscordUserInfo::class.java)
        )
    }

    /**
     * Create a new GuideUser with WebUser info
     */
    @Transactional
    fun createWithWebUser(
        guideUserData: GuideUserData,
        webUserData: WebUserData
    ): GuideUserWithWebUser {
        // Determine labels based on the WebUserData subtype
        val webUserLabels = if (webUserData is AnonymousWebUserData) "WebUser:Anonymous" else "WebUser"

        val cypher = """
            CREATE (u:GuideUser)
            SET u += ${'$'}guideUserProps
            CREATE (w:$webUserLabels)
            SET w += ${'$'}webUserProps
            CREATE (u)-[:IS_WEB_USER]->(w)
            RETURN {
              guideUserData: properties(u),
              webUser: properties(w)
            }
            """

        return manager.getOne(
            QuerySpecification
                .withStatement(cypher)
                .bindObject("guideUserProps", guideUserData)
                .bindObject("webUserProps", webUserData)
                .transform(GuideUserWithWebUser::class.java)
        )
    }

    /**
     * Update GuideUser persona
     */
    @Transactional
    fun updatePersona(guideUserId: String, persona: String) {
        val cypher = """
            MATCH (u:GuideUser {id: ${'$'}id})
            SET u.persona = ${'$'}persona
            """

        manager.execute(
            QuerySpecification
                .withStatement(cypher)
                .bind(mapOf("id" to guideUserId, "persona" to persona))
                .transform(Void::class.java)
        )
    }

    /**
     * Update GuideUser custom prompt
     */
    @Transactional
    fun updateCustomPrompt(guideUserId: String, customPrompt: String) {
        val cypher = """
            MATCH (u:GuideUser {id: ${'$'}id})
            SET u.customPrompt = ${'$'}customPrompt
            """

        manager.execute(
            QuerySpecification
                .withStatement(cypher)
                .bind(mapOf("id" to guideUserId, "customPrompt" to customPrompt))
        )
    }

    /**
     * Find a GuideUser by ID, returning as GuideUserWithWebUser or GuideUserWithDiscord
     * depending on what relationships exist
     */
    @Transactional(readOnly = true)
    fun findById(id: String): Optional<HasGuideUserData> {
        val cypher = """
            MATCH (u:GuideUser {id: ${'$'}id})
            OPTIONAL MATCH (u)-[:IS_DISCORD_USER]->(d:DiscordUserInfo)
            OPTIONAL MATCH (u)-[:IS_WEB_USER]->(w:WebUser)
            RETURN {
              guideUserData: properties(u),
              discordUserInfo: properties(d),
              webUser: properties(w)
            }
            """

        val result = manager.optionalGetOne(
            QuerySpecification
                .withStatement(cypher)
                .bind(mapOf("id" to id))
                .transform(Map::class.java)
        )

        @Suppress("UNCHECKED_CAST")
        return result.map { mapper.mapToGuideUserComposite(it as Map<String, Any?>) }
    }

    /**
     * Save a GuideUser - this is a simplified version that updates persona/customPrompt
     */
    @Transactional
    fun save(guideUser: GuideUserData): HasGuideUserData {
        val cypher = """
            MERGE (u:GuideUser {id: ${'$'}id})
            SET u += props
            """

        manager.execute(
            QuerySpecification
                .withStatement(cypher)
                .bindObject("props", guideUser)
                .bind(mapOf("id" to guideUser.id))
        )

        // Return the updated user
        return findById(guideUser.id!!).orElseThrow()
    }

    /**
     * Delete all GuideUsers (for testing) - also deletes related WebUser and DiscordUserInfo nodes
     */
    @Transactional
    fun deleteAllGuideUsers() {
        val cypher = """
            MATCH (u:GuideUser)
            OPTIONAL MATCH (u)-[:IS_WEB_USER]->(w:WebUser)
            OPTIONAL MATCH (u)-[:IS_DISCORD_USER]->(d:DiscordUserInfo)
            DETACH DELETE u, w, d
            """

        manager.execute(
            QuerySpecification
                .withStatement(cypher)
                .bind(emptyMap<String, Any>())
        )
    }

    /**
     * Delete GuideUsers where username starts with the given prefix (for test cleanup)
     */
    @Transactional
    fun deleteByUsernameStartingWith(prefix: String) {
        val cypher = """
            MATCH (u:GuideUser)-[:IS_WEB_USER]->(w:WebUser)
            WHERE toLower(w.userName) STARTS WITH toLower(${'$'}prefix)
            DETACH DELETE u, w
            """

        manager.execute(
            QuerySpecification
                .withStatement(cypher)
                .bind(mapOf("prefix" to prefix))
        )
    }

    /**
     * Find all GuideUsers (for testing)
     */
    @Transactional(readOnly = true)
    fun findAllGuideUsers(): List<HasGuideUserData> {
        val cypher = """
            MATCH (u:GuideUser)
            OPTIONAL MATCH (u)-[:IS_DISCORD_USER]->(d:DiscordUserInfo)
            OPTIONAL MATCH (u)-[:IS_WEB_USER]->(w:WebUser)
            RETURN {
              guideUserData: properties(u),
              discordUserInfo: properties(d),
              webUser: properties(w)
            }
            """

        @Suppress("UNCHECKED_CAST")
        return manager.query(
            QuerySpecification
                .withStatement(cypher)
                .bind(emptyMap<String, Any>())
                .transform(Map::class.java)
        ).map { mapper.mapToGuideUserComposite(it as Map<String, Any?>) }
    }
}
