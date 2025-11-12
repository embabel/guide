package com.embabel.guide.domain;

import com.embabel.guide.domain.drivine.*;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class GuideUserService {

    private final DrivineGuideUserRepository guideUserRepository;

    public GuideUserService(DrivineGuideUserRepository guideUserRepository) {
        this.guideUserRepository = guideUserRepository;
    }

    /**
     * Returns the anonymous web user for non-authenticated sessions.
     * If the user doesn't exist yet, creates it with a random UUID and displayName "Friend".
     * <p>
     * Synchronized to prevent race condition where multiple concurrent requests
     * could create duplicate GuideUser instances.
     *
     * @return the anonymous web user GuideUser
     */
    public synchronized GuideUserWithWebUser findOrCreateAnonymousWebUser() {
        return guideUserRepository.findAnonymousWebUser()
            .orElseGet(() -> {
                // Double-check after acquiring lock to avoid duplicate creation
                var existing = guideUserRepository.findAnonymousWebUser();
                if (existing.isPresent()) {
                    return existing.get();
                }

                var guideUser = new GuideUserData(UUID.randomUUID().toString(), null, null);
                var anonymousWebUser = new AnonymousWebUserData(UUID.randomUUID().toString(), "Friend", "anonymous", null, null, null);

                return guideUserRepository.createWithWebUser(guideUser, anonymousWebUser);
            });
    }

    /**
     * Finds a GuideUser by their WebUser ID.
     *
     * @param webUserId the WebUser's ID
     * @return the HasGuideUser composite if found
     */
    public Optional<HasGuideUserData> findByWebUserId(String webUserId) {
        return guideUserRepository.findByWebUserId(webUserId)
            .map(composed -> (HasGuideUserData) composed);
    }

    /**
     * Creates and saves a new GuideUser from a WebUser.
     *
     * @param webUser the WebUser to create a GuideUser from
     * @return the saved GuideUser composite
     */
    public GuideUserWithWebUser saveFromWebUser(WebUserData webUser) {
        var guideUser = new GuideUserData(UUID.randomUUID().toString(), null, null);
        return guideUserRepository.createWithWebUser(guideUser, webUser);
    }

    /**
     * Finds a GuideUser by their username.
     *
     * @param username the username to search for
     * @return the GuideUser if found, null otherwise
     */
    public Optional<GuideUserWithWebUser> findByWebUserName(String username) {
        return guideUserRepository.findByWebUserName(username);
    }

    /**
     * Updates the persona for a user.
     *
     * @param userId  the user's ID
     * @param persona the persona name to set
     * @return the updated GuideUser
     */
    public HasGuideUserData updatePersona(String userId, String persona) {
        guideUserRepository.updatePersona(userId, persona);
        return guideUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    /**
     * Saves a GuideUser.
     *
     * @param guideUser the GuideUser to save
     * @return the saved HasGuideUser composite
     */
    public HasGuideUserData saveUser(GuideUser guideUser) {
        return guideUserRepository.save(guideUser);
    }

}
