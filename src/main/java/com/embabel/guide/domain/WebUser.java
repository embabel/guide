/*
 * Copyright 2024-2025 Embabel Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.guide.domain;

import com.embabel.agent.identity.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

/**
 * Represents a user connecting through the web interface.
 *
 * This implementation of the User interface allows web-based users to interact
 * with the Guide chatbot through WebSocket connections.
 */
@NodeEntity("WebUser")
public class WebUser implements User {

    @Id
    private String userId;
    private String userDisplayName;
    private String userUsername;
    private String userEmail;
    private String passwordHash;
    private String refreshToken;

    @Nullable
    @Relationship(type = "IS_WEB_USER", direction = Relationship.Direction.INCOMING)
    private GuideUser guideUser;


    // No-arg constructor for Neo4j OGM
    public WebUser() {
        this.userId = "";
    }

    public WebUser(String userId, String userDisplayName, String userUsername, String userEmail, String passwordHash, String refreshToken) {
        this.userId = userId;
        this.userDisplayName = userDisplayName;
        this.userUsername = userUsername;
        this.userEmail = userEmail;
        this.passwordHash = passwordHash;
        this.refreshToken = refreshToken;
    }

    @NotNull
    @Override
    public String getId() {
        return userId;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return userDisplayName != null ? userDisplayName :
               (userUsername != null ? userUsername : userId);
    }

    @NotNull
    @Override
    public String getUsername() {
        return userUsername != null ? userUsername : userId;
    }

    @Nullable
    @Override
    public String getEmail() {
        return userEmail;
    }

    @Override
    public String toString() {
        return "WebUser(id='" + userId + "', displayName='" + getDisplayName() + "')";
    }

    @Nullable
    public GuideUser getGuideUser() {
        return guideUser;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserDisplayName() {
        return userDisplayName;
    }

    public String getUserUsername() {
        return userUsername;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

}
