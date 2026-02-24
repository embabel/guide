package com.embabel.guide.domain

import org.springframework.stereotype.Service
import java.util.Optional
import java.util.UUID

@Service
class GuideUserService(
    private val guideUserRepository: GuideUserRepository
) {

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

                guideUserRepository.createWithWebUser(guideUser, anonymousWebUser)
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
     * Finds a GuideUser by their WebUser ID.
     *
     * @param webUserId the WebUser's ID
     * @return the GuideUser if found
     */
    fun findByWebUserId(webUserId: String): Optional<GuideUser> {
        return guideUserRepository.findByWebUserId(webUserId)
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
            displayName = webUser.displayName
        )
        return guideUserRepository.createWithWebUser(guideUser, webUser)
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

    /**
     * Updates the persona for a user.
     *
     * @param userId  the user's ID
     * @param persona the persona name to set
     * @return the updated GuideUser
     */
    fun updatePersona(userId: String, persona: String): GuideUser {
        guideUserRepository.updatePersona(userId, persona)
        return guideUserRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }
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
