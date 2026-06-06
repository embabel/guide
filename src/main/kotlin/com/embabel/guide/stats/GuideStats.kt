package com.embabel.guide.stats

import com.embabel.agent.rag.store.ContentElementRepositoryInfo
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonUnwrapped

/**
 * Public stats payload for `GET /api/v1/data/stats`.
 *
 * The content counts ([content]) are unwrapped to the top level so the JSON is byte-for-byte
 * identical to what the endpoint returned before (it serialized the same object directly) — the
 * front-end keeps reading `chunkCount` etc. unchanged.
 *
 * The remaining fields are admin-only and are populated solely when the caller has the ADMIN role.
 * With [JsonInclude.Include.NON_NULL] they are omitted entirely for everyone else, so the default
 * (non-admin) response is exactly the legacy payload — no user/message figures leak to the public.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class GuideStats(
    @get:JsonUnwrapped val content: ContentElementRepositoryInfo,
    val userCount: Long? = null,
    val onlineCount: Int? = null,
    val messageCount: Long? = null,
    val topUsersThisWeek: List<TopMessagingUser>? = null,
)

/** A user ranked by messages sent in the trailing window. Admin-only. */
data class TopMessagingUser(
    val displayName: String,
    val messageCount: Long,
)