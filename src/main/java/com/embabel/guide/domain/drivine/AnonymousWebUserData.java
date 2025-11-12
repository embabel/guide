package com.embabel.guide.domain.drivine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Data representation for anonymous web users.
 * Extends WebUserData to add the Anonymous label distinction.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnonymousWebUserData extends WebUserData {

    // No-arg constructor for Jackson
    // TODO: I think jackson module covers this?
    public AnonymousWebUserData() {
        super();
    }

    public AnonymousWebUserData(String userId, String userDisplayName, String userUsername,
                                @Nullable String userEmail, @Nullable String passwordHash,
                                @Nullable String refreshToken) {
        super(userId, userDisplayName, userUsername, userEmail, passwordHash, refreshToken);
    }

    @Override
    public String toString() {
        return "AnonymousWebUserData{" +
            "userId='" + getId() + '\'' +
            ", userDisplayName='" + getDisplayName() + '\'' +
            ", userUsername='" + getUserName() + '\'' +
            '}';
    }
}
