package com.embabel.guide.domain.drivine;

import org.jetbrains.annotations.NotNull;

/**
 * Marker interface for types that contain web user information.
 * Provides access to the web user data component.
 */
public interface HasWebUserData {

    /**
     * Get the web user data
     * @return the web user data
     */
    @NotNull
    WebUserData getWebUser();
}
