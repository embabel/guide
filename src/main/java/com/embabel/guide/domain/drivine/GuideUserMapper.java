package com.embabel.guide.domain.drivine;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Mapper for converting Neo4j query results to GuideUser composite types.
 */
@Component
public class GuideUserMapper {

    /**
     * Maps a raw query result map to the appropriate GuideUser composite type.
     * Checks for Discord and WebUser relationships and returns the appropriate composite.
     */
    public HasGuideUserData mapToGuideUserComposite(Map<String, Object> map) {
        Map<String, Object> userDataMap = getNestedMap(map, "guideUserData");
        GuideUserData userData = mapToGuideUserData(userDataMap);

        Map<String, Object> discordMap = getNestedMap(map, "discordUserInfo");
        if (discordMap != null) {
            DiscordUserInfoData discordData = mapToDiscordUserInfoData(discordMap);
            return new GuideUserWithDiscordUserInfo(userData, discordData);
        }

        Map<String, Object> webUserMap = getNestedMap(map, "webUser");
        if (webUserMap != null) {
            WebUserData webUserData = mapToWebUserData(webUserMap);
            return new GuideUserWithWebUser(userData, webUserData);
        }

        // Return a plain GuideUserData when there are no relationships
        return userData;
    }

    /**
     * Maps a property map to GuideUserData using constructor.
     */
    private GuideUserData mapToGuideUserData(Map<String, Object> map) {
        if (map == null) {
            return new GuideUserData(null, null, null);
        }
        return new GuideUserData(
            (String) map.get("id"),
            (String) map.get("persona"),
            (String) map.get("customPrompt")
        );
    }

    /**
     * Maps a property map to DiscordUserInfoData using constructor.
     */
    private DiscordUserInfoData mapToDiscordUserInfoData(Map<String, Object> map) {
        return new DiscordUserInfoData(
            (String) map.get("id"),
            (String) map.get("username"),
            (String) map.get("discriminator"),
            (String) map.get("displayName"),
            (Boolean) map.get("isBot"),
            (String) map.get("avatarUrl")
        );
    }

    /**
     * Maps a property map to WebUserData using constructor.
     */
    private WebUserData mapToWebUserData(Map<String, Object> map) {
        return new WebUserData(
            (String) map.get("id"),
            (String) map.get("displayName"),
            (String) map.get("userName"),
            (String) map.get("userEmail"),
            (String) map.get("passwordHash"),
            (String) map.get("refreshToken")
        );
    }

    /**
     * Safely extracts a nested map from a parent map.
     * Suppresses unchecked cast warning as Neo4j query results are untyped.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getNestedMap(Map<String, Object> map, String key) {
        return (Map<String, Object>) map.get(key);
    }
}
