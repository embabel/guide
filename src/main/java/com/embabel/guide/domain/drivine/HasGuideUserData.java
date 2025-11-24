package com.embabel.guide.domain.drivine;

import org.jetbrains.annotations.NotNull;

/**
 * Interface for composed result types that contain a GuideUser.
 * Allows for polymorphic handling of different query result compositions.
 */
public interface HasGuideUserData {

    /**
     * Get the GuideUser from this composed result
     * @return The GuideUser instance
     */
    @NotNull
    GuideUserData guideUserData();
}
