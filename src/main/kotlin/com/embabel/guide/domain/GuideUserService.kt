package com.embabel.guide.domain

import org.springframework.stereotype.Service
import java.util.Optional
import java.util.UUID

@Service
class GuideUserService(
    private val guideUserRepository: GuideUserRepository,
    private val personaRepository: PersonaRepository,
) {

    companion object {
        const val DEFAULT_PERSONA_NAME = "adaptive"
    }

    /**
     * Returns the anonymous web user for non-authenticated sessions.
     * If the user doesn't exist yet, creates it with a random UUID and displayName "Friend".
     *
     * Synchronized to prevent race condition where multiple concurrent requests
     * could create duplicate GuideUser instances.
     *
     * @return the anonymous web user GuideUser
     */
    @Synchronized
    fun findOrCreateAnonymousWebUser(): GuideUser {
        return guideUserRepository.findAnonymousWebUser()
            .orElseGet {
                // Double-check after acquiring lock to avoid duplicate creation
                val existing = guideUserRepository.findAnonymousWebUser()
                if (existing.isPresent) {
                    return@orElseGet existing.get()
                }

                val guideUser = GuideUserData(
                    id = UUID.randomUUID().toString(),
                    displayName = "Friend"
                )
                val anonymousWebUser = AnonymousWebUserData(
                    UUID.randomUUID().toString(),
                    "Friend",
                    "anonymous",
                    null,
                    null,
                    null
                )

                guideUserRepository.createWithWebUser(guideUser, anonymousWebUser, resolveDefaultPersona())
            }
    }

    /**
     * Finds a GuideUser by their ID.
     *
     * @param id the GuideUser's ID
     * @return the GuideUser if found
     */
    fun findById(id: String): Optional<GuideUser> {
        return guideUserRepository.findById(id)
    }

    /**
     * Lightweight find by ID — skips USES_PERSONA traversal.
     */
    fun findWebUserById(id: String): Optional<GuideWebUser> {
        return guideUserRepository.findWebUserById(id)
    }

    /**
     * Finds a GuideUser by their WebUser ID.
     *
     * @param webUserId the WebUser's ID
     * @return the GuideUser if found
     */
    fun findByWebUserId(webUserId: String): Optional<GuideUser> {
        return guideUserRepository.findByWebUserId(webUserId)
    }

    /**
     * Creates and saves a new GuideUser from a Discord user.
     *
     * @param guideUserData the GuideUser core data
     * @param discordUserInfo the Discord identity info
     * @return the saved GuideUser
     */
    fun saveFromDiscordUser(guideUserData: GuideUserData, discordUserInfo: DiscordUserInfoData): GuideUser {
        return guideUserRepository.createWithDiscord(guideUserData, discordUserInfo, resolveDefaultPersona())
    }

    /**
     * Creates and saves a new GuideUser from a WebUser.
     *
     * @param webUser the WebUser to create a GuideUser from
     * @return the saved GuideUser
     */
    fun saveFromWebUser(webUser: WebUserData): GuideUser {
        val guideUser = GuideUserData(
            id = UUID.randomUUID().toString(),
            displayName = webUser.displayName,
        )
        val defaultPersona = resolveDefaultPersona()
        return guideUserRepository.createWithWebUser(guideUser, webUser, defaultPersona)
    }

    /**
     * Finds a GuideUser by their username.
     *
     * @param username the username to search for
     * @return the GuideUser if found
     */
    fun findByWebUserName(username: String): Optional<GuideUser> {
        return guideUserRepository.findByWebUserName(username)
    }

    /**
     * Finds a GuideUser by their web user email.
     *
     * @param userEmail the email to search for
     * @return the GuideUser if found
     */
    fun findByWebUserEmail(userEmail: String): Optional<GuideUser> {
        return guideUserRepository.findByWebUserEmail(userEmail)
    }

    fun findByOAuthProvider(provider: String, providerUserId: String): Optional<GuideUser> {
        return guideUserRepository.findByOAuthProvider(provider, providerUserId)
    }

    fun findByEmailVerificationToken(token: String): Optional<GuideUser> {
        return guideUserRepository.findByEmailVerificationToken(token)
    }

    /**
     * Updates the persona for a user.
     *
     * @param userId    the user's ID
     * @param personaId the persona ID to set
     * @return the updated GuideUser
     */
    fun updatePersona(userId: String, personaId: String): GuideUser {
        val personaData = personaRepository.findById(personaId)?.persona
            ?: throw IllegalArgumentException("Persona not found: $personaId")
        guideUserRepository.updatePersona(userId, personaData)
        return guideUserRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }
    }

    private fun resolveDefaultPersona(): PersonaData {
        val view = personaRepository.findByNameAndOwner(DEFAULT_PERSONA_NAME, PersonaRepository.SYSTEM_OWNER_ID)
            ?: personaRepository.findByOwner(PersonaRepository.SYSTEM_OWNER_ID).firstOrNull()
            ?: error("No system personas found — has PersonaSeedingService run?")
        return view.persona
    }

    /**
     * Saves a GuideUser.
     *
     * @param guideUser the GuideUser to save
     * @return the saved GuideUser
     */
    fun saveUser(guideUser: GuideUser): GuideUser {
        return guideUserRepository.save(guideUser)
    }
}
