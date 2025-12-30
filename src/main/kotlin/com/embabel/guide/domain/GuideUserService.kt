package com.embabel.guide.domain

import org.springframework.stereotype.Service
import java.util.Optional
import java.util.UUID

@Service
class GuideUserService(
    private val guideUserRepository: DrivineGuideUserRepository
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
    fun findOrCreateAnonymousWebUser(): GuideUserWithWebUser {
        return guideUserRepository.findAnonymousWebUser()
            .orElseGet {
                // Double-check after acquiring lock to avoid duplicate creation
                val existing = guideUserRepository.findAnonymousWebUser()
                if (existing.isPresent) {
                    return@orElseGet existing.get()
                }

                val guideUser = GuideUserData(UUID.randomUUID().toString(), null, null)
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
     * Finds a GuideUser by their WebUser ID.
     *
     * @param webUserId the WebUser's ID
     * @return the HasGuideUser composite if found
     */
    fun findByWebUserId(webUserId: String): Optional<HasGuideUserData> {
        return guideUserRepository.findByWebUserId(webUserId)
            .map { it as HasGuideUserData }
    }

    /**
     * Creates and saves a new GuideUser from a WebUser.
     *
     * @param webUser the WebUser to create a GuideUser from
     * @return the saved GuideUser composite
     */
    fun saveFromWebUser(webUser: WebUserData): GuideUserWithWebUser {
        val guideUser = GuideUserData(UUID.randomUUID().toString(), null, null)
        return guideUserRepository.createWithWebUser(guideUser, webUser)
    }

    /**
     * Finds a GuideUser by their username.
     *
     * @param username the username to search for
     * @return the GuideUser if found, null otherwise
     */
    fun findByWebUserName(username: String): Optional<GuideUserWithWebUser> {
        return guideUserRepository.findByWebUserName(username)
    }

    /**
     * Updates the persona for a user.
     *
     * @param userId  the user's ID
     * @param persona the persona name to set
     * @return the updated GuideUser
     */
    fun updatePersona(userId: String, persona: String): HasGuideUserData {
        guideUserRepository.updatePersona(userId, persona)
        return guideUserRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }
    }

    /**
     * Saves a GuideUser.
     *
     * @param guideUser the GuideUser to save
     * @return the saved HasGuideUser composite
     */
    fun saveUser(guideUser: GuideUserData): HasGuideUserData {
        return guideUserRepository.save(guideUser)
    }
}
