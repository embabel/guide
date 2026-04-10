package com.embabel.guide.domain

import java.util.Optional

/**
 * Repository interface for GuideUser operations.
 * Uses the unified GuideUser GraphView type.
 */
interface GuideUserRepository {

    /**
     * Find a GuideUser by Discord user ID
     */
    fun findByDiscordUserId(discordUserId: String): Optional<GuideUser>

    /**
     * Find a GuideUser by web user ID
     */
    fun findByWebUserId(webUserId: String): Optional<GuideUser>

    /**
     * Find the anonymous web user
     */
    fun findAnonymousWebUser(): Optional<GuideUser>

    /**
     * Find a GuideUser by web username
     */
    fun findByWebUserName(userName: String): Optional<GuideUser>

    /**
     * Find a GuideUser by web user email
     */
    fun findByWebUserEmail(userEmail: String): Optional<GuideUser>

    /**
     * Find a GuideUser by OAuth provider and provider user ID
     */
    fun findByOAuthProvider(provider: String, providerUserId: String): Optional<GuideUser>

    /**
     * Find a GuideUser by email verification token
     */
    fun findByEmailVerificationToken(token: String): Optional<GuideUser>

    /**
     * Find a GuideUser by ID
     */
    fun findById(id: String): Optional<GuideUser>

    /**
     * Lightweight find by ID — skips USES_PERSONA traversal.
     */
    fun findWebUserById(id: String): Optional<GuideWebUser>

    /**
     * Lightweight find by web user ID — skips USES_PERSONA traversal.
     */
    fun findWebUserByWebUserId(webUserId: String): Optional<GuideWebUser>

    /**
     * Create a new GuideUser with Discord info
     */
    fun createWithDiscord(
        guideUserData: GuideUserData,
        discordUserInfo: DiscordUserInfoData,
        persona: PersonaData,
    ): GuideUser

    /**
     * Create a new GuideUser with WebUser info
     */
    fun createWithWebUser(
        guideUserData: GuideUserData,
        webUserData: WebUserData,
        persona: PersonaData,
    ): GuideUser

    /**
     * Save a GuideUser (updates the core data)
     */
    fun save(guideUser: GuideUser): GuideUser

    /**
     * Update GuideUser persona
     */
    fun updatePersona(guideUserId: String, persona: PersonaData)

    /**
     * Update GuideUser custom prompt
     */
    fun updateCustomPrompt(guideUserId: String, customPrompt: String)

    /**
     * Find all GuideUsers
     */
    fun findAll(): List<GuideUser>

    /**
     * Delete all GuideUsers (for testing)
     */
    fun deleteAll()

    /**
     * Delete GuideUsers where web username starts with prefix (for test cleanup)
     */
    fun deleteByUsernameStartingWith(prefix: String)
}