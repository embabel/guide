package com.embabel.guide.util

import com.embabel.agent.discord.DiscordUser
import com.embabel.guide.domain.DiscordUserInfoData
import com.embabel.guide.domain.GuideUserData
import java.util.UUID

fun DiscordUser.toGuideUserData(): GuideUserData = GuideUserData(
    UUID.randomUUID().toString(),
    discordUser.displayName,
    discordUser.username,
    null,  // email
    null,  // persona
    null,  // customPrompt
)

fun DiscordUser.toDiscordUserInfoData(): DiscordUserInfoData = DiscordUserInfoData(
    discordUser.id,
    discordUser.username,
    discordUser.discriminator,
    discordUser.displayName,
    discordUser.isBot,
    discordUser.avatarUrl,
)
