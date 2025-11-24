package com.embabel.guide.domain.drivine;

import com.embabel.agent.api.identity.User;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Composed result type for GuideUser queries that include web user information.
 * Following Drivine's composition philosophy rather than ORM relationships.
 * Implements User interface by delegating to the WebUser data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GuideUserWithWebUser implements HasGuideUserData, HasWebUserData, User {

    @JsonProperty
    private GuideUserData guideUserData;

    @JsonProperty
    private WebUserData webUser;

    // No-arg constructor for Jackson
    public GuideUserWithWebUser() {
    }

    public GuideUserWithWebUser(GuideUserData guideUserData, WebUserData webUser) {
        this.guideUserData = guideUserData;
        this.webUser = webUser;
    }

    @Override
    @NotNull
    public GuideUserData guideUserData() {
        return guideUserData;
    }

    public void setGuideUserData(GuideUserData guideUserData) {
        this.guideUserData = guideUserData;
    }

    @NotNull
    @Override
    public WebUserData getWebUser() {
        return webUser;
    }

    public void setWebUser(WebUserData webUser) {
        this.webUser = webUser;
    }

    @Override
    public String toString() {
        return "GuideUserWithWebUser{" +
                "guideUserData=" + guideUserData +
                ", webUser=" + webUser +
                '}';
    }

    // User interface delegation to WebUser

    @NotNull
    @Override
    public String getId() {
        return webUser.getId();
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return webUser.getDisplayName();
    }

    @NotNull
    @Override
    public String getUsername() {
        return webUser.getUserName();
    }

    @Nullable
    @Override
    public String getEmail() {
        return webUser.getUserEmail();
    }
}
