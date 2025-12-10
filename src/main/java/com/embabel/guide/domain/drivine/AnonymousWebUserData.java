package com.embabel.guide.domain.drivine;

import org.drivine.annotation.NodeFragment;
import org.jetbrains.annotations.Nullable;

/**
 * Data representation for anonymous web users.
 * Extends WebUserData to add the Anonymous label distinction.
 * Has both WebUser and Anonymous labels in Neo4j.
 */
@NodeFragment(labels = {"WebUser", "Anonymous"})
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
