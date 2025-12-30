package com.embabel.guide.domain

import org.springframework.stereotype.Component

/**
 * Mapper for converting Neo4j query results to GuideUser composite types.
 */
@Component
class GuideUserMapper {

    /**
     * Maps a raw query result map to the appropriate GuideUser composite type.
     * Checks for Discord and WebUser relationships and returns the appropriate composite.
     */
    fun mapToGuideUserComposite(map: Map<String, Any?>): HasGuideUserData {
        val userDataMap = getNestedMap(map, "guideUserData")
        val userData = mapToGuideUserData(userDataMap)

        val discordMap = getNestedMap(map, "discordUserInfo")
        if (discordMap != null) {
            val discordData = mapToDiscordUserInfoData(discordMap)
            return GuideUserWithDiscordUserInfo(userData, discordData)
        }

        val webUserMap = getNestedMap(map, "webUser")
        if (webUserMap != null) {
            val webUserData = mapToWebUserData(webUserMap)
            return GuideUserWithWebUser(userData, webUserData)
        }

        // Return a plain GuideUserData when there are no relationships
        return userData
    }

    private fun mapToGuideUserData(map: Map<String, Any?>?): GuideUserData {
        requireNotNull(map) { "guideUserData map cannot be null" }
        return GuideUserData(
            id = map["id"] as String,
            persona = map["persona"] as? String,
            customPrompt = map["customPrompt"] as? String
        )
    }

    private fun mapToDiscordUserInfoData(map: Map<String, Any?>): DiscordUserInfoData {
        return DiscordUserInfoData(
            id = map["id"] as? String,
            username = map["username"] as? String,
            discriminator = map["discriminator"] as? String,
            displayName = map["displayName"] as? String,
            isBot = map["isBot"] as? Boolean,
            avatarUrl = map["avatarUrl"] as? String
        )
    }

    private fun mapToWebUserData(map: Map<String, Any?>): WebUserData {
        return WebUserData(
            id = map["id"] as String,
            displayName = map["displayName"] as String,
            userName = map["userName"] as String,
            userEmail = map["userEmail"] as? String,
            passwordHash = map["passwordHash"] as? String,
            refreshToken = map["refreshToken"] as? String
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun getNestedMap(map: Map<String, Any?>, key: String): Map<String, Any?>? {
        return map[key] as? Map<String, Any?>
    }
}
