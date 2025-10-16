package com.embabel.guide.domain;

import org.springframework.stereotype.Service;

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

}
