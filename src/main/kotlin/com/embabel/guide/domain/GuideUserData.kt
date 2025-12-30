package com.embabel.guide.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Simple data representation of GuideUser properties for Drivine composition.
 * Maps directly to node properties without OGM relationships.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GuideUserData(
    var id: String,
    var persona: String? = null,
    var customPrompt: String? = null
) : HasGuideUserData {

    override fun guideUserData(): GuideUserData = this
}
