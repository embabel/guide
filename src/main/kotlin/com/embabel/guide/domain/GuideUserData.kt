package com.embabel.guide.domain

import com.embabel.chat.store.model.StoredUser
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.drivine.annotation.Default
import org.drivine.annotation.NodeFragment
import org.drivine.annotation.NodeId

/**
 * Node fragment representing a GuideUser in the graph.
 * Implements StoredUser to enable integration with embabel-chat-store library.
 */
@NodeFragment(labels = ["GuideUser", "User"])
@JsonIgnoreProperties(ignoreUnknown = true)
data class GuideUserData(
    @NodeId
    override var id: String,
    override var displayName: String = "",
    override var username: String = displayName,
    override var email: String? = null,
    var customPrompt: String? = null,
    var welcomed: Boolean = false,
    // Authorization roles (e.g. "ADMIN"). Granted out-of-band via Cypher; absent on legacy
    // nodes, so @Default coalesces a missing/null property to an empty list. See GuideUserCache
    // TTL for how an out-of-band grant becomes visible to the running app.
    @Default
    var roles: List<String> = emptyList(),
) : HasGuideUserData, StoredUser {

    override fun guideUserData(): GuideUserData = this
}
