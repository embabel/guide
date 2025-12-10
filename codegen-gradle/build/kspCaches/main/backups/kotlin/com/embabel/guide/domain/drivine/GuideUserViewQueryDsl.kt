// Generated code - do not modify
package com.embabel.guide.domain.drivine

import kotlin.Unit
import kotlin.collections.List
import org.drivine.manager.GraphObjectManager
import org.drivine.query.dsl.GraphQuerySpec

public class GuideUserViewQueryDsl {
  public val guideUser: GuideUserDataProperties = GuideUserDataProperties()

  public val webUser: WebUserDataProperties = WebUserDataProperties("webUser")

  public val discordUserInfo: DiscordUserInfoDataProperties =
      DiscordUserInfoDataProperties("discordUserInfo")

  public companion object {
    public val INSTANCE: GuideUserViewQueryDsl = GuideUserViewQueryDsl()
  }
}

public inline fun <reified T : GuideUserView> GraphObjectManager.loadAll(noinline
    spec: GraphQuerySpec<GuideUserViewQueryDsl>.() -> Unit): List<T> = loadAll(T::class.java,
    GuideUserViewQueryDsl.INSTANCE, spec)
