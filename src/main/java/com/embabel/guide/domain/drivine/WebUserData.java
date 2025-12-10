package com.embabel.guide.domain.drivine;

import org.drivine.annotation.NodeFragment;
import org.jetbrains.annotations.Nullable;

/**
 * Simple data representation of WebUser properties for Drivine composition.
 */
@NodeFragment(labels = {"WebUser"})
public class WebUserData {

    private String id;
    private String displayName;
    private String userName;

    @Nullable
    private String userEmail;

    @Nullable
    private String passwordHash;

    @Nullable
    private String refreshToken;

    // No-arg constructor for Jackson
    public WebUserData() {
    }

    public WebUserData(String userId, String userDisplayName, String userUsername,
                      @Nullable String userEmail, @Nullable String passwordHash,
                      @Nullable String refreshToken) {
        this.id = userId;
        this.displayName = userDisplayName;
        this.userName = userUsername;
        this.userEmail = userEmail;
        this.passwordHash = passwordHash;
        this.refreshToken = refreshToken;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Nullable
    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(@Nullable String userEmail) {
        this.userEmail = userEmail;
    }

    @Nullable
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(@Nullable String passwordHash) {
        this.passwordHash = passwordHash;
    }

    @Nullable
    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(@Nullable String refreshToken) {
        this.refreshToken = refreshToken;
    }

    @Override
    public String toString() {
        return "WebUserData{" +
                "userId='" + id + '\'' +
                ", userDisplayName='" + displayName + '\'' +
                ", userUsername='" + userName + '\'' +
                '}';
    }
}
