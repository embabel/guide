package com.embabel.guide.domain;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GuideUserService {

  private GuideUserRepository guideUserRepository;

  public GuideUserService(GuideUserRepository guideUserRepository) {
    this.guideUserRepository = guideUserRepository;
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
  public synchronized GuideUser findOrCreateAnonymousWebUser() {
    return guideUserRepository.findAnonymousWebUser()
        .orElseGet(() -> {
          // Double-check after acquiring lock to avoid duplicate creation
          var existing = guideUserRepository.findAnonymousWebUser();
          if (existing.isPresent()) {
            return existing.get();
          }

          // Create new anonymous web user
          WebUser anonymousWebUser = AnonymousWebUser.create();
          GuideUser guideUser = GuideUser.createFromWebUser(anonymousWebUser);

          return guideUserRepository.save(guideUser);
        });
  }

  /**
   * Finds a GuideUser by their WebUser ID.
   *
   * @param webUserId the WebUser's ID
   * @return the GuideUser if found
   */
  public Optional<GuideUser> findByWebUserId(String webUserId) {
    return guideUserRepository.findByWebUserId(webUserId);
  }

  /**
   * Creates and saves a new GuideUser from a WebUser.
   *
   * @param webUser the WebUser to create a GuideUser from
   * @return the saved GuideUser
   */
  public GuideUser saveFromWebUser(WebUser webUser) {
    GuideUser guideUser = GuideUser.createFromWebUser(webUser);
    return guideUserRepository.save(guideUser);
  }

  /**
   * Finds a GuideUser by their username.
   *
   * @param username the username to search for
   * @return the GuideUser if found, null otherwise
   */
  public GuideUser findByUsername(String username) {
    return guideUserRepository.findByUsername(username).orElse(null);
  }

  /**
   * Updates the persona for a user.
   *
   * @param userId the user's ID
   * @param persona the persona name to set
   * @return the updated GuideUser
   */
  public GuideUser updatePersona(String userId, String persona) {
    GuideUser user = guideUserRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    user.setPersona(persona);
    return guideUserRepository.save(user);
  }

}
