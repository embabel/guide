package com.embabel.guide.domain

/**
 * Interface for composed result types that contain a GuideUser.
 * Allows for polymorphic handling of different query result compositions.
 */
interface HasGuideUserData {

    /**
     * Get the GuideUser from this composed result
     * @return The GuideUser instance
     */
    fun guideUserData(): GuideUserData
}
