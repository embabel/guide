// Generated code - do not modify
package com.embabel.guide.domain.drivine

import kotlin.Boolean
import kotlin.String
import org.drivine.query.dsl.PropertyReference
import org.drivine.query.dsl.StringPropertyReference

public class GuideUserDataProperties {
  public val id: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference("guideUser", "id")

  public val persona: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference("guideUser", "persona")

  public val customPrompt: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference("guideUser", "customPrompt")
}

public class WebUserDataProperties(
  private val alias: String,
) {
  public val id: StringPropertyReference = org.drivine.query.dsl.StringPropertyReference(alias,
      "id")

  public val displayName: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "displayName")

  public val userName: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "userName")

  public val userEmail: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "userEmail")

  public val passwordHash: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "passwordHash")

  public val refreshToken: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "refreshToken")
}

public class DiscordUserInfoDataProperties(
  private val alias: String,
) {
  public val id: StringPropertyReference = org.drivine.query.dsl.StringPropertyReference(alias,
      "id")

  public val username: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "username")

  public val discriminator: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "discriminator")

  public val displayName: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "displayName")

  public val isBot: PropertyReference<Boolean> =
      org.drivine.query.dsl.PropertyReference<kotlin.Boolean>(alias, "isBot")

  public val avatarUrl: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "avatarUrl")
}
