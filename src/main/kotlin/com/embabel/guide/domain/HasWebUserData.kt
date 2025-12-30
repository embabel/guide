package com.embabel.guide.domain

/**
 * Marker interface for types that contain web user information.
 * Provides access to the web user data component.
 */
interface HasWebUserData {

    /**
     * Get the web user data
     * @return the web user data
     */
    val webUser: WebUserData
}
